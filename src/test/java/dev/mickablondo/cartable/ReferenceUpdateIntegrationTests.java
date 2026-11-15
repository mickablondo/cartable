package dev.mickablondo.cartable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.reference.ReferenceIndexed;
import dev.mickablondo.cartable.reference.ReferencePassage;
import dev.mickablondo.cartable.reference.ReferenceSearchService;
import dev.mickablondo.cartable.reference.ReferenceUpdateReport;
import dev.mickablondo.cartable.reference.ReferenceUpdateService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({ TestcontainersConfiguration.class, ReferenceUpdateIntegrationTests.FakeEmbeddingConfiguration.class })
@RecordApplicationEvents
@SpringBootTest
class ReferenceUpdateIntegrationTests {

	private static final Path REFERENCE_DIRECTORY = Path.of(
		System.getProperty("java.io.tmpdir"),
		"cartable-reference-update-tests-" + UUID.randomUUID());
	private static final Map<String, HttpResponse> HTTP_RESPONSES = new ConcurrentHashMap<>();
	private static final HttpServer HTTP_SERVER = startServer();

	@Autowired
	private ReferenceUpdateService referenceUpdateService;

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
	static void configureReferenceUpdate(DynamicPropertyRegistry registry) {
		registry.add("cartable.reference.data-directory", REFERENCE_DIRECTORY::toString);
		registry.add("cartable.reference.documents[0].id", () -> "success");
		registry.add("cartable.reference.documents[0].title", () -> "Programme officiel de test");
		registry.add("cartable.reference.documents[0].subject", () -> "HISTORY_GEOGRAPHY");
		registry.add("cartable.reference.documents[0].pdf-url", () -> url("/pdf/success"));
		registry.add("cartable.reference.documents[0].page-url", () -> "https://example.test/success");
		registry.add("cartable.reference.documents[0].publication-date", () -> "2025-09-01");
		registry.add("cartable.reference.documents[0].license", () -> "Licence Ouverte 2.0");
		registry.add("cartable.reference.documents[0].class", () -> "3e");
		registry.add("cartable.reference.documents[0].school-years[0]", () -> "2024-2025");
		registerDocument(registry, 1, "forbidden", "/pdf/forbidden");
		registerDocument(registry, 2, "not-pdf", "/pdf/not-pdf");
		registerDocument(registry, 3, "unchanged", "/pdf/unchanged");
	}

	@BeforeEach
	void resetFixtures() throws IOException {
		Files.createDirectories(REFERENCE_DIRECTORY);
		try (var paths = Files.list(REFERENCE_DIRECTORY)) {
			for (Path path : paths.toList()) {
				Files.delete(path);
			}
		}
		jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'origin' = 'OFFICIAL_REFERENCE'");
		jdbcTemplate.update("DELETE FROM reference_document");
		fakeEmbeddingModel.reset();
		HTTP_RESPONSES.put("/pdf/success",
			new HttpResponse(200, pdfDocument("Chapitre 1 - Repères\nLa République est un régime politique.")));
		HTTP_RESPONSES.put("/pdf/forbidden", new HttpResponse(403, "Accès interdit".getBytes(StandardCharsets.UTF_8)));
		HTTP_RESPONSES.put("/pdf/not-pdf", new HttpResponse(200, "<html>pas un PDF</html>".getBytes(StandardCharsets.UTF_8)));
		HTTP_RESPONSES.put("/pdf/unchanged", new HttpResponse(200, pdfDocument("Document inchangé.")));
	}

	@AfterAll
	static void removeTemporaryFilesAndServer() throws IOException {
		HTTP_SERVER.stop(0);
		if (Files.exists(REFERENCE_DIRECTORY)) {
			try (var paths = Files.walk(REFERENCE_DIRECTORY)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
	}

	@Test
	void downloadsAndIndexesValidPdfWhileReporting403AndNonPdfResponses() {
		ReferenceUpdateReport result = referenceUpdateService.updateReference();

		assertEquals(2, result.updatedDocuments());
		assertEquals(0, result.unchangedDocuments());
		assertEquals(2, result.issues().size());
		assertTrue(result.issues().stream().anyMatch(issue -> issue.documentId().equals("forbidden")
			&& issue.pdfUrl().equals(url("/pdf/forbidden"))
			&& issue.reason().contains("HTTP 403")
			&& issue.reason().contains("data/reference/forbidden.pdf")));
		assertTrue(result.issues().stream().anyMatch(issue -> issue.documentId().equals("not-pdf")
			&& issue.reason().contains("n'est pas un fichier PDF")));
		assertFalse(result.issues().stream().anyMatch(issue -> issue.documentId().equals("unchanged")));

		assertTrue(Files.exists(REFERENCE_DIRECTORY.resolve("success.pdf")));
		assertTrue(Files.exists(REFERENCE_DIRECTORY.resolve("unchanged.pdf")));
		assertFalse(Files.exists(REFERENCE_DIRECTORY.resolve("not-pdf.pdf")));
		assertEquals(2, jdbcTemplate.queryForObject("SELECT count(*) FROM reference_document", Integer.class));
		assertEquals(2, applicationEvents.stream(ReferenceIndexed.class).count());

		Map<String, Object> metadata = jdbcTemplate.queryForMap("""
			SELECT metadata ->> 'sourceUrl' AS source_url,
			       metadata ->> 'pdfUrl' AS pdf_url,
			       metadata ->> 'retrievedAt' AS retrieved_at,
			       metadata ->> 'class' AS school_class,
			       metadata ->> 'schoolYears' AS school_years
			FROM vector_store WHERE metadata ->> 'sourceUrl' = 'https://example.test/success'
			""");
		assertEquals("https://example.test/success", metadata.get("source_url"));
		assertEquals(url("/pdf/success"), metadata.get("pdf_url"));
		assertEquals(LocalDate.now().toString(), metadata.get("retrieved_at"));
		assertEquals("3e", metadata.get("school_class"));
		assertEquals("2024-2025", metadata.get("school_years"));

		List<ReferencePassage> passages = referenceSearchService.search("République", Subject.HISTORY_GEOGRAPHY);
		assertTrue(passages.stream().anyMatch(passage ->
			passage.sourceUrl().equals("https://example.test/success")
				&& passage.sourceFile().equals("success.pdf")));
	}

	@Test
	void unchangedPdfIsNotReembeddedAndDoesNotCreateDuplicateVectors() {
		ReferenceUpdateReport firstUpdate = referenceUpdateService.updateReference();
		int embeddingCallsAfterFirstUpdate = fakeEmbeddingModel.callCount();
		jdbcTemplate.update("""
			UPDATE reference_document SET retrieved_at = DATE '2000-01-01'
			WHERE source_url = 'https://example.test/success'
			""");

		ReferenceUpdateReport secondUpdate = referenceUpdateService.updateReference();

		assertEquals(2, firstUpdate.updatedDocuments());
		assertEquals(0, secondUpdate.updatedDocuments());
		assertEquals(2, secondUpdate.unchangedDocuments());
		assertEquals(embeddingCallsAfterFirstUpdate, fakeEmbeddingModel.callCount());
		assertEquals(1, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceUrl' = 'https://example.test/success'
			""", Integer.class));
		assertEquals(LocalDate.now(), jdbcTemplate.queryForObject("""
			SELECT retrieved_at FROM reference_document WHERE source_url = 'https://example.test/success'
			""", LocalDate.class));
		assertEquals(2, applicationEvents.stream(ReferenceIndexed.class).count());
	}

	@Test
	void changedPdfReplacesExistingPassagesOnReimport() {
		referenceUpdateService.updateReference();
		HTTP_RESPONSES.put("/pdf/success",
			new HttpResponse(200, pdfDocument("Chapitre 2 - Reperes\nLa Republique possede une constitution.")));

		ReferenceUpdateReport result = referenceUpdateService.updateReference();

		assertEquals(1, result.updatedDocuments());
		assertEquals(1, result.unchangedDocuments());
		assertEquals(1, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceUrl' = 'https://example.test/success'
			""", Integer.class));
		assertEquals("Chapitre 2 - Reperes",
			jdbcTemplate.queryForObject("""
				SELECT metadata ->> 'chapter' FROM vector_store
				WHERE metadata ->> 'sourceUrl' = 'https://example.test/success'
				""", String.class));
		assertEquals(3, applicationEvents.stream(ReferenceIndexed.class).count());
	}

	@Test
	void usesManuallyDepositedPdfWhenDirectDownloadFails() throws IOException {
		Files.write(REFERENCE_DIRECTORY.resolve("forbidden.pdf"), pdfDocument("PDF déposé manuellement."));

		ReferenceUpdateReport result = referenceUpdateService.updateReference();

		assertTrue(result.issues().stream().anyMatch(issue -> issue.documentId().equals("forbidden")
			&& issue.localPdfUsed()));
		assertEquals(1, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM reference_document WHERE source_url = 'https://example.test/forbidden'
			""", Integer.class));
		assertEquals(1, jdbcTemplate.queryForObject("""
			SELECT count(*) FROM vector_store WHERE metadata ->> 'sourceUrl' = 'https://example.test/forbidden'
			""", Integer.class));
	}

	private static void registerDocument(DynamicPropertyRegistry registry, int index, String id, String path) {
		String prefix = "cartable.reference.documents[" + index + "].";
		registry.add(prefix + "id", () -> id);
		registry.add(prefix + "title", () -> "Référence " + id);
		registry.add(prefix + "subject", () -> "HISTORY_GEOGRAPHY");
		registry.add(prefix + "pdf-url", () -> url(path));
		registry.add(prefix + "page-url", () -> "https://example.test/" + id);
		registry.add(prefix + "publication-date", () -> "2025-09-01");
		registry.add(prefix + "license", () -> "Licence Ouverte 2.0");
		registry.add(prefix + "class", () -> "3e");
		registry.add(prefix + "school-years[0]", () -> "2024-2025");
	}

	private static HttpServer startServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/pdf/", exchange -> {
				HttpResponse response = HTTP_RESPONSES.get(exchange.getRequestURI().getPath());
				if (response == null) {
					exchange.sendResponseHeaders(404, -1);
					exchange.close();
					return;
				}
				exchange.sendResponseHeaders(response.status(), response.body().length);
				try (var output = exchange.getResponseBody()) {
					output.write(response.body());
				}
			});
			server.start();
			return server;
		}
		catch (IOException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static String url(String path) {
		return "http://127.0.0.1:" + HTTP_SERVER.getAddress().getPort() + path;
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

	private record HttpResponse(int status, byte[] body) {
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

		void reset() {
			callCount.set(0);
		}

		private static float[] fakeVector() {
			float[] vector = new float[1024];
			vector[0] = 1.0f;
			return vector;
		}

	}

}
