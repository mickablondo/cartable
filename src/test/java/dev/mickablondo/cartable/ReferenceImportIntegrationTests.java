package dev.mickablondo.cartable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.reference.ReferenceImportException;
import dev.mickablondo.cartable.reference.ReferenceImportResult;
import dev.mickablondo.cartable.reference.ReferenceImportService;
import dev.mickablondo.cartable.reference.ReferenceIndexed;
import dev.mickablondo.cartable.reference.ReferencePassage;
import dev.mickablondo.cartable.reference.ReferenceSearchService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({ TestcontainersConfiguration.class, ReferenceImportIntegrationTests.FakeEmbeddingConfiguration.class })
@RecordApplicationEvents
@SpringBootTest
class ReferenceImportIntegrationTests {

	private static final Path REFERENCE_DIRECTORY = Path.of(
		System.getProperty("java.io.tmpdir"),
		"cartable-reference-import-tests-" + UUID.randomUUID());

	@Autowired
	private ReferenceImportService referenceImportService;

	@Autowired
	private ReferenceSearchService referenceSearchService;

	@Autowired
	private VectorStore vectorStore;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationEvents applicationEvents;

	@Autowired
	private FakeEmbeddingModel fakeEmbeddingModel;

	@DynamicPropertySource
	static void configureReferenceDirectory(DynamicPropertyRegistry registry) {
		registry.add("cartable.reference.data-directory", REFERENCE_DIRECTORY::toString);
	}

	@BeforeEach
	void clearReferenceDirectory() throws IOException {
		if (Files.exists(REFERENCE_DIRECTORY)) {
			try (var paths = Files.list(REFERENCE_DIRECTORY)) {
				for (Path path : paths.toList()) {
					Files.delete(path);
				}
			}
		}
		else {
			Files.createDirectories(REFERENCE_DIRECTORY);
		}
	}

	@AfterAll
	static void removeTemporaryFiles() throws IOException {
		if (Files.exists(REFERENCE_DIRECTORY)) {
			try (var paths = Files.walk(REFERENCE_DIRECTORY)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
	}

	@Test
	void importsPdfWithYamlMetadataAndIndexesOfficialReference() throws IOException {
		String sourceUrl = "https://example.test/programme";
		writeReference("programme", "Reference officielle de test.", sourceUrl,
			"Programme officiel de test", "HISTORY_GEOGRAPHY");

		ReferenceImportResult result = referenceImportService.importReferences();

		assertEquals(1, result.importedDocuments());
		assertEquals(0, result.skippedDocuments());
		assertEquals(1, result.indexedChunks());
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT count(*) FROM reference_document WHERE source_url = ?", Integer.class, sourceUrl));

		List<Map<String, Object>> passages = jdbcTemplate.queryForList("""
			SELECT content, metadata ->> 'origin' AS origin, metadata ->> 'title' AS title,
			       metadata ->> 'sourceUrl' AS source_url, metadata ->> 'retrievedAt' AS retrieved_at,
			       metadata ->> 'license' AS license, metadata ->> 'subject' AS subject,
			       metadata ->> 'publicationDate' AS publication_date,
			       metadata ->> 'sourceFile' AS source_file
			FROM vector_store
			WHERE metadata ->> 'sourceUrl' = ?
			""", sourceUrl);

		assertEquals(1, passages.size());
		Map<String, Object> passage = passages.getFirst();
		assertEquals("Reference officielle de test.", passage.get("content"));
		assertEquals("OFFICIAL_REFERENCE", passage.get("origin"));
		assertEquals("Programme officiel de test", passage.get("title"));
		assertEquals(sourceUrl, passage.get("source_url"));
		assertEquals("2026-11-14", passage.get("retrieved_at"));
		assertEquals("Licence Ouverte 2.0", passage.get("license"));
		assertEquals("HISTORY_GEOGRAPHY", passage.get("subject"));
		assertEquals("2025-09-01", passage.get("publication_date"));
		assertEquals("programme.pdf", passage.get("source_file"));
		assertEquals(1, applicationEvents.stream(ReferenceIndexed.class).count());
	}

	@Test
	void reimportingUnchangedPdfDoesNotCreateDuplicateVectorsOrCallEmbeddingAgain() throws IOException {
		String sourceUrl = "https://example.test/idempotent";
		writeReference("idempotent", "Reference à importer une seule fois.", sourceUrl,
			"Référence sans doublon", "FRENCH");

		ReferenceImportResult firstImport = referenceImportService.importReferences();
		int embeddingCallsAfterFirstImport = fakeEmbeddingModel.callCount();
		ReferenceImportResult secondImport = referenceImportService.importReferences();

		assertEquals(1, firstImport.importedDocuments());
		assertEquals(1, secondImport.skippedDocuments());
		assertEquals(0, secondImport.importedDocuments());
		assertEquals(embeddingCallsAfterFirstImport, fakeEmbeddingModel.callCount());
		assertEquals(1, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceUrl' = ?
			""", Integer.class, sourceUrl));
		assertEquals(1, applicationEvents.stream(ReferenceIndexed.class).count());
	}

	@Test
	void reimportingChangedPdfReplacesExistingPassagesForTheSameSourceUrl() throws IOException {
		String sourceUrl = "https://example.test/updated";
		writeReference("updated", "Ancienne version officielle.", sourceUrl,
			"Ancien titre", "MATHEMATICS");
		referenceImportService.importReferences();

		writeReference("updated", "Nouvelle version officielle.", sourceUrl,
			"Nouveau titre", "MATHEMATICS");
		ReferenceImportResult result = referenceImportService.importReferences();

		assertEquals(1, result.importedDocuments());
		assertEquals(1, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceUrl' = ?
			""", Integer.class, sourceUrl));
		Map<String, Object> passage = jdbcTemplate.queryForMap("""
			SELECT content, metadata ->> 'title' AS title FROM vector_store
			WHERE metadata ->> 'sourceUrl' = ?
			""", sourceUrl);
		assertEquals("Nouvelle version officielle.", passage.get("content"));
		assertEquals("Nouveau titre", passage.get("title"));
		assertEquals(2, applicationEvents.stream(ReferenceIndexed.class).count());
	}

	@Test
	void removesSurplusChunksWhenAnUpdatedPdfBecomesShorter() throws IOException {
		String sourceUrl = "https://example.test/shortened";
		writeReference("shortened", "mot ".repeat(800), sourceUrl,
			"Référence longue", "FRENCH");

		ReferenceImportResult firstImport = referenceImportService.importReferences();
		assertTrue(firstImport.indexedChunks() > 1);
		assertEquals(firstImport.indexedChunks(), jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceUrl' = ?
			""", Integer.class, sourceUrl));

		writeReference("shortened", "Version courte.", sourceUrl, "Référence courte", "FRENCH");
		ReferenceImportResult update = referenceImportService.importReferences();

		assertEquals(1, update.indexedChunks());
		assertEquals(1, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceUrl' = ?
			""", Integer.class, sourceUrl));
	}

	@Test
	void searchesOfficialPassagesBySubjectAndOriginWithTheirSource() throws IOException {
		String sourceUrl = "https://example.test/technology-search";
		writeReference(
			"technology-search",
			"Chapitre 1 - Objets techniques\nLes objets techniques repondent a un besoin.",
			sourceUrl,
			"Référentiel de technologie",
			"TECHNOLOGY");
		referenceImportService.importReferences();
		vectorStore.add(List.of(
			Document.builder()
				.text("Cours artificiel de technologie.")
				.metadata(Map.of(
					"origin", "COURSE",
					"subject", Subject.TECHNOLOGY.name(),
					"sourceFile", "technology-course.docx",
					"chapter", "Chapitre du cours"))
				.build(),
			Document.builder()
				.text("Référence officielle d'une autre matière.")
				.metadata(Map.of(
					"origin", "OFFICIAL_REFERENCE",
					"subject", Subject.FRENCH.name(),
					"title", "Référence de français",
					"sourceUrl", "https://example.test/french",
					"sourceFile", "french-reference.pdf",
					"chapter", "Chapitre de français",
					"publicationDate", "2025-09-01"))
				.build()));

		List<ReferencePassage> passages = referenceSearchService.search("objets techniques", Subject.TECHNOLOGY);

		assertEquals(1, passages.size());
		ReferencePassage passage = passages.getFirst();
		assertEquals("Les objets techniques repondent a un besoin.", passage.text());
		assertEquals("Référentiel de technologie", passage.title());
		assertEquals(sourceUrl, passage.sourceUrl());
		assertEquals("technology-search.pdf", passage.sourceFile());
		assertEquals("Chapitre 1 - Objets techniques", passage.chapter());
		assertEquals(LocalDate.parse("2025-09-01"), passage.publicationDate());
	}

	@Test
	void rejectsPdfWithoutMatchingYamlDescriptor() throws IOException {
		Files.write(REFERENCE_DIRECTORY.resolve("orphan.pdf"), pdfDocument("Reference sans métadonnées."));

		ReferenceImportException exception = assertThrows(ReferenceImportException.class,
			() -> referenceImportService.importReferences());

		assertTrue(exception.getMessage().contains("doit être accompagné"));
	}

	private static void writeReference(
			String baseName, String pdfText, String sourceUrl, String title, String subject) throws IOException {
		Files.write(REFERENCE_DIRECTORY.resolve(baseName + ".pdf"), pdfDocument(pdfText));
		Files.writeString(REFERENCE_DIRECTORY.resolve(baseName + ".yaml"), """
			title: "%s"
			sourceUrl: "%s"
			retrievedAt: "2026-11-14"
			license: "Licence Ouverte 2.0"
			subject: "%s"
			publicationDate: "2025-09-01"
			""".formatted(title, sourceUrl, subject));
	}

	private static byte[] pdfDocument(String text) {
		String content = "BT /F1 12 Tf 72 720 Td "
			+ text.lines()
				.map(line -> "(" + line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
					+ ") Tj 0 -14 Td ")
				.reduce("", String::concat)
			+ "ET";
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

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeEmbeddingConfiguration {

		@Bean
		@Primary
		FakeEmbeddingModel fakeEmbeddingModel() {
			return new FakeEmbeddingModel();
		}

	}

	static class FakeEmbeddingModel implements EmbeddingModel {

		private final AtomicInteger callCount = new AtomicInteger();

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			callCount.incrementAndGet();
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

		int callCount() {
			return callCount.get();
		}

		private static float[] fakeVector() {
			float[] vector = new float[1024];
			vector[0] = 1.0f;
			return vector;
		}

	}

}
