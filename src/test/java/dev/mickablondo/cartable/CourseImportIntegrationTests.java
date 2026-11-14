package dev.mickablondo.cartable;

import dev.mickablondo.cartable.course.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@Import({ TestcontainersConfiguration.class, CourseImportIntegrationTests.FakeEmbeddingConfiguration.class })
@RecordApplicationEvents
@SpringBootTest
class CourseImportIntegrationTests {

	private static final Path DATA_DIRECTORY = Path.of(
		System.getProperty("java.io.tmpdir"),
		"cartable-course-import-tests-" + UUID.randomUUID());

	@Autowired
	private CourseImportService courseImportService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationEvents applicationEvents;

	@Autowired
	private FakeEmbeddingModel fakeEmbeddingModel;

	@DynamicPropertySource
	static void configureStorageDirectory(DynamicPropertyRegistry registry) {
		registry.add("cartable.course.data-directory", DATA_DIRECTORY::toString);
	}

	@AfterAll
	static void removeTemporaryFiles() throws IOException {
		if (Files.exists(DATA_DIRECTORY)) {
			try (var paths = Files.walk(DATA_DIRECTORY)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
	}

	@Test
	void importsPdfStoresTextAndPublishesCourseIdOnlyEvent(@TempDir Path inputDirectory) throws Exception {
		String expectedText = "Cours artificiel en PDF";
		Path pdf = inputDirectory.resolve("course.pdf");
		Files.write(pdf, pdfDocument(expectedText));

		CourseImportResult result = courseImportService.importCourse(pdf, Subject.HISTORY_GEOGRAPHY);

		assertStoredCourse(result, expectedText, pdf);
		assertEquals(1, applicationEvents.stream(CourseImported.class).count());
		assertEquals(result.id(), applicationEvents.stream(CourseImported.class).findFirst().orElseThrow().courseId());
		assertEquals(1, applicationEvents.stream(CourseIndexed.class)
			.filter(event -> event.courseId().equals(result.id()))
			.findFirst()
			.orElseThrow()
			.indexedChunks());
	}

	@Test
	void importsDocxAndStoresExtractedText(@TempDir Path inputDirectory) throws Exception {
		String expectedText = "Cours artificiel dans un document Word";
		Path docx = inputDirectory.resolve("course.docx");
		Files.write(docx, wordDocument(expectedText));

		CourseImportResult result = courseImportService.importCourse(docx, Subject.FRENCH);

		assertStoredCourse(result, expectedText, docx);
	}

	@Test
	void splitsAtExplicitChapterTitlesAndIndexesCourseMetadata(@TempDir Path inputDirectory) throws Exception {
		Path docx = inputDirectory.resolve("chapters.docx");
		Files.write(docx, wordDocument("""
			Chapitre 1 - Test
			Notion synthétique du premier chapitre.
			Partie 2 - Test
			Notion synthétique de la deuxième partie.
			"""));

		CourseImportResult result = courseImportService.importCourse(docx, Subject.HISTORY_GEOGRAPHY);
		awaitCourseIndexed(result.id());

		List<Map<String, Object>> passages = jdbcTemplate.queryForList("""
			SELECT content, metadata ->> 'origin' AS origin, metadata ->> 'subject' AS subject,
			       metadata ->> 'level' AS level, metadata ->> 'chapter' AS chapter,
			       metadata ->> 'sourceFile' AS source_file
			FROM vector_store
			WHERE metadata ->> 'sourceFile' = ?
			""", docx.getFileName().toString());

		assertEquals(2, passages.size());
		assertTrue(passages.stream().anyMatch(passage ->
			"Chapitre 1 - Test".equals(passage.get("chapter"))));
		assertTrue(passages.stream().anyMatch(passage ->
			"Partie 2 - Test".equals(passage.get("chapter"))));
		for (Map<String, Object> passage : passages) {
			assertEquals("COURSE", passage.get("origin"));
			assertEquals(Subject.HISTORY_GEOGRAPHY.name(), passage.get("subject"));
			assertEquals("3ème", passage.get("level"));
			assertEquals(docx.getFileName().toString(), passage.get("source_file"));
		}
	}

	@Test
	void keepsFailedIndexingPublicationAvailableForRetry(@TempDir Path inputDirectory) throws Exception {
		Path docx = inputDirectory.resolve("failed-indexing.docx");
		Files.write(docx, wordDocument("Cours artificiel pour tester l'échec."));
		fakeEmbeddingModel.failNextCall();

		CourseImportResult result = courseImportService.importCourse(docx, Subject.FRENCH);
		awaitCondition(() -> hasFailedPublication(result.id()), "La publication Modulith doit rester en échec.");

		String status = jdbcTemplate.queryForObject("SELECT status FROM course WHERE id = ?",
			String.class, result.id());
		assertEquals("IMPORTED", status);
		assertEquals(0, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceFile' = ?
			""", Integer.class, docx.getFileName().toString()));
	}

	@Test
	void rejectsEmptyFile(@TempDir Path inputDirectory) throws IOException {
		Path emptyFile = inputDirectory.resolve("empty-course.pdf");
		Files.createFile(emptyFile);

		CourseImportException exception = assertThrows(CourseImportException.class,
			() -> courseImportService.importCourse(emptyFile, Subject.MATHEMATICS));

		assertEquals(CourseImportException.Reason.EMPTY_FILE, exception.getReason());
		assertEquals("Le fichier du cours est vide.", exception.getMessage());
	}

	@Test
	void rejectsUnsupportedFormat(@TempDir Path inputDirectory) throws IOException {
		Path textFile = inputDirectory.resolve("unsupported-course.txt");
		Files.writeString(textFile, "Texte de test");

		CourseImportException exception = assertThrows(CourseImportException.class,
			() -> courseImportService.importCourse(textFile, Subject.MATHEMATICS));

		assertEquals(CourseImportException.Reason.UNSUPPORTED_FORMAT, exception.getReason());
	}

	@Test
	void rejectsPdfWithoutExtractableTextAsUnsupportedScan(@TempDir Path inputDirectory) throws IOException {
		Path scannedPdf = inputDirectory.resolve("scanned-course.pdf");
		Files.write(scannedPdf, pdfDocument(""));

		CourseImportException exception = assertThrows(CourseImportException.class,
			() -> courseImportService.importCourse(scannedPdf, Subject.MATHEMATICS));

		assertEquals(CourseImportException.Reason.SCAN_NOT_SUPPORTED, exception.getReason());
		assertTrue(exception.getMessage().contains("scan non supporté avant la V2"));
	}

	@Test
	void requiresSubject(@TempDir Path inputDirectory) throws IOException {
		Path pdf = inputDirectory.resolve("course.pdf");
		Files.write(pdf, pdfDocument("Cours artificiel"));

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> courseImportService.importCourse(pdf, null));

		assertEquals("La matière est obligatoire.", exception.getMessage());
	}

	private void assertStoredCourse(CourseImportResult result, String expectedText, Path originalFile)
			throws IOException, InterruptedException {
		awaitCourseIndexed(result.id());
		var course = jdbcTemplate.queryForMap("""
			SELECT file_name, subject, status, source_path, extracted_text
			FROM course WHERE id = ?
			""", result.id());

		assertEquals(originalFile.getFileName().toString(), course.get("file_name"));
		assertEquals(result.subject().name(), course.get("subject"));
		assertEquals("INDEXED", course.get("status"));
		assertEquals(expectedText, course.get("extracted_text"));
		assertEquals(expectedText, courseImportService.getExtractedText(result.id()));

		String storedPath = (String) course.get("source_path");
		assertFalse(Path.of(storedPath).isAbsolute());
		assertTrue(storedPath.startsWith("courses/"));
		assertEquals(-1L, Files.mismatch(DATA_DIRECTORY.resolve(storedPath), originalFile));

		List<Map<String, Object>> passages = jdbcTemplate.queryForList("""
			SELECT metadata ->> 'origin' AS origin, metadata ->> 'subject' AS subject,
			       metadata ->> 'level' AS level, metadata ->> 'chapter' AS chapter,
			       metadata ->> 'sourceFile' AS source_file
			FROM vector_store
			WHERE metadata ->> 'sourceFile' = ?
			""", originalFile.getFileName().toString());
		assertEquals(1, passages.size());
		Map<String, Object> metadata = passages.getFirst();
		assertEquals("COURSE", metadata.get("origin"));
		assertEquals(result.subject().name(), metadata.get("subject"));
		assertEquals("3ème", metadata.get("level"));
		assertEquals("Sans chapitre détecté", metadata.get("chapter"));
		assertEquals(originalFile.getFileName().toString(), metadata.get("source_file"));
	}

	private void awaitCourseIndexed(UUID courseId) throws InterruptedException {
		awaitCondition(() -> "INDEXED".equals(jdbcTemplate.queryForObject(
			"SELECT status FROM course WHERE id = ?", String.class, courseId)),
			"Le cours doit être indexé.");
	}

	private boolean hasFailedPublication(UUID courseId) {
		return jdbcTemplate.queryForList("""
			SELECT status FROM event_publication WHERE serialized_event LIKE ?
			""", "%" + courseId + "%").stream()
			.anyMatch(publication -> "FAILED".equals(publication.get("status")));
	}

	private static void awaitCondition(BooleanSupplier condition, String failureMessage) throws InterruptedException {
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(25L);
		}
		assertTrue(condition.getAsBoolean(), failureMessage);
	}

	private static byte[] pdfDocument(String text) {
		String content = text.isEmpty()
			? ""
			: "BT /F1 12 Tf 72 720 Td (" + text + ") Tj ET";
		String stream = content + "\n";
		String[] objects = {
			"<< /Type /Catalog /Pages 2 0 R >>",
			"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
			"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
			"<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n"
				+ stream + "endstream",
			"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
		};
		StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
		int[] offsets = new int[objects.length + 1];
		for (int index = 0; index < objects.length; index++) {
			offsets[index + 1] = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
			pdf.append(index + 1).append(" 0 obj\n").append(objects[index]).append("\nendobj\n");
		}
		int xrefOffset = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
		pdf.append("xref\n0 ").append(objects.length + 1).append("\n")
			.append("0000000000 65535 f \n");
		for (int index = 1; index < offsets.length; index++) {
			pdf.append("%010d 00000 n \n".formatted(offsets[index]));
		}
		pdf.append("trailer\n<< /Size ").append(objects.length + 1)
			.append(" /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF\n");
		return pdf.toString().getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] wordDocument(String text) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(output)) {
			addZipEntry(zip, "[Content_Types].xml", """
				<?xml version="1.0" encoding="UTF-8"?>
				<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
				  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
				  <Default Extension="xml" ContentType="application/xml"/>
				  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
				</Types>
				""");
			addZipEntry(zip, "_rels/.rels", """
				<?xml version="1.0" encoding="UTF-8"?>
				<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
				  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
				</Relationships>
				""");
			addZipEntry(zip, "word/document.xml", """
				<?xml version="1.0" encoding="UTF-8"?>
				<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
				  <w:body>%s<w:sectPr/></w:body>
				</w:document>
				""".formatted(text.lines()
					.map(line -> "<w:p><w:r><w:t>%s</w:t></w:r></w:p>".formatted(escapeXml(line)))
					.reduce("", String::concat)));
		}
		return output.toByteArray();
	}

	private static String escapeXml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeEmbeddingConfiguration {

		@Bean
		@Primary
		FakeEmbeddingModel fakeEmbeddingModel() {
			return new FakeEmbeddingModel();
		}

	}

	static class FakeEmbeddingModel implements EmbeddingModel {

		private final AtomicBoolean failNextCall = new AtomicBoolean();

		void failNextCall() {
			failNextCall.set(true);
		}

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			if (failNextCall.compareAndSet(true, false)) {
				throw new IllegalStateException("Échec d'embedding simulé.");
			}
			List<Embedding> embeddings = java.util.stream.IntStream.range(0, request.getInstructions().size())
				.mapToObj(index -> new Embedding(fakeVector(), index))
				.toList();
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public float[] embed(Document document) {
			return fakeVector();
		}

		@Override
		public int dimensions() {
			return 1024;
		}

		private static float[] fakeVector() {
			float[] vector = new float[1024];
			vector[0] = 1.0f;
			return vector;
		}

	}
}
