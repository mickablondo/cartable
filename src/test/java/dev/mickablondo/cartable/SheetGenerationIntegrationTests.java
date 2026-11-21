package dev.mickablondo.cartable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.NoCoursePassagesFoundException;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetCourseContent;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetGenerated;
import dev.mickablondo.cartable.sheet.SheetGenerationService;
import dev.mickablondo.cartable.sheet.SheetKnowledgeOrigin;
import dev.mickablondo.cartable.sheet.SheetMarkdownExportService;
import dev.mickablondo.cartable.sheet.SheetQueryService;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.SheetVerificationService;
import dev.mickablondo.cartable.sheet.SheetVerified;
import dev.mickablondo.cartable.sheet.UnavailableSheet;
import dev.mickablondo.cartable.sheet.internal.model.SheetDraft;
import dev.mickablondo.cartable.sheet.internal.model.SheetGeneralKnowledgeDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.PromptSystemSpec;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import({ TestcontainersConfiguration.class, SheetGenerationIntegrationTests.FakeGenerationConfiguration.class })
@RecordApplicationEvents
@SpringBootTest
@TestPropertySource(properties = "cartable.ai.require-local-models=false")
class SheetGenerationIntegrationTests {

	@Autowired
	private SheetGenerationService sheetGenerationService;

	@Autowired
	private SheetVerificationService sheetVerificationService;

	@Autowired
	private SheetMarkdownExportService sheetMarkdownExportService;

	@Autowired
	private SheetQueryService sheetQueryService;

	@Autowired
	private ChatClient chatClient;

	@Autowired
	private VectorStore vectorStore;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationEvents applicationEvents;

	private final List<String> suppliedPrompts = new ArrayList<>();

	@Value("classpath:prompts/sheet-generation-common.st")
	private Resource commonPrompt;

	@Value("classpath:prompts/sheet-generation-generic.st")
	private Resource genericPrompt;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM sheet");
		jdbcTemplate.update("DELETE FROM vector_store");
		suppliedPrompts.clear();
		clearInvocations(chatClient);
		vectorStore.add(List.of(
			Document.builder()
				.text("Les objets techniques répondent à un besoin.")
				.metadata(Map.of(
					"origin", "COURSE",
					"subject", Subject.TECHNOLOGY.name(),
					"level", "3ème",
					"chapter", "Chapitre 1 - Objets techniques",
					"sourceFile", "technologie.docx"))
				.build(),
			Document.builder()
				.text("Le programme étudie les objets techniques.")
				.metadata(Map.of(
					"origin", "OFFICIAL_REFERENCE",
					"subject", Subject.TECHNOLOGY.name(),
					"title", "Programme de technologie",
					"sourceUrl", "https://example.test/technology",
					"sourceFile", "programme.pdf",
					"chapter", "Objets techniques",
					"publicationDate", "2025-09-01"))
				.build()));
	}

	@Test
	void generatesAndStoresSheetWithCitationsAndPublishesEvent() {
		fixChatClientResponse(new SheetDraft(
			"Objets techniques",
			new SheetCourseContent(
				List.of(new SheetStatement("Ils répondent à un besoin.", List.of("C1"))),
				List.of(new SheetDefinition("Objet technique", "Objet conçu pour répondre à un besoin.", List.of("R1"))),
				List.of()),
			List.of(new SheetGeneralKnowledgeDraft("Un objet peut aussi être évalué selon son impact.", List.of())),
			List.of()));

		Sheet sheet = sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY);

		assertEquals(Subject.TECHNOLOGY, sheet.subject());
		assertEquals("Objets techniques", sheet.theme());
		assertEquals("TO_VERIFY", sheet.status().name());
		assertEquals(2, sheet.sources().size());
		assertEquals(SheetKnowledgeOrigin.GENERAL_KNOWLEDGE, sheet.generalKnowledge().getFirst().origin());
		assertEquals("technologie.docx", sheet.sources().getFirst().sourceFile());
		assertEquals("https://example.test/technology", sheet.sources().get(1).sourceUrl());
		assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM sheet WHERE id = ?", Integer.class, sheet.id()));

		Map<String, Object> stored = jdbcTemplate.queryForMap("""
			SELECT subject, status, sheet_type, schema_version,
			       variant_data -> 'definitions' -> 0 ->> 'term' AS variant_definition,
			       key_concepts -> 0 -> 'sourceIds' ->> 0 AS concept_source,
			       definitions -> 0 -> 'sourceIds' ->> 0 AS definition_source,
			       general_knowledge -> 0 ->> 'origin' AS general_origin,
			       general_knowledge -> 0 -> 'sourceIds' AS general_sources,
			       jsonb_array_length(sources) AS source_count
			FROM sheet
			WHERE id = ?
			""", sheet.id());
		assertEquals("TECHNOLOGY", stored.get("subject"));
		assertEquals("TO_VERIFY", stored.get("status"));
		assertEquals("GENERIC", stored.get("sheet_type"));
		assertEquals(1, stored.get("schema_version"));
		assertEquals("Objet technique", stored.get("variant_definition"));
		assertEquals("C1", stored.get("concept_source"));
		assertEquals("R1", stored.get("definition_source"));
		assertEquals("GENERAL_KNOWLEDGE", stored.get("general_origin"));
		assertNull(stored.get("general_sources"));
		assertEquals(2, stored.get("source_count"));
		assertEquals(1, applicationEvents.stream(SheetGenerated.class)
			.filter(event -> event.sheetId().equals(sheet.id()))
			.count());
	}

	@Test
	void refusesSheetWithoutCitedSources() {
		fixChatClientResponse(new SheetDraft(
			"Objets techniques",
			new SheetCourseContent(
				List.of(new SheetStatement("Ils répondent à un besoin.", List.of())),
				List.of(),
				List.of()),
			List.of(),
			List.of("Le référentiel ne précise pas davantage ce thème.")));

		assertThrows(IllegalStateException.class,
			() -> sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY));

		assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM sheet", Integer.class));
		assertEquals(0, applicationEvents.stream(SheetGenerated.class).count());
	}

	@Test
	void refusesCourseContentWithUnknownSourceId() {
		fixChatClientResponse(new SheetDraft(
			"Objets techniques",
			new SheetCourseContent(
				List.of(new SheetStatement("Un fait non étayé.", List.of("C99"))),
				List.of(),
				List.of()),
			List.of(),
			List.of()));

		assertThrows(IllegalStateException.class,
			() -> sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY));
		assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM sheet", Integer.class));
	}

	@Test
	void refusesGeneralKnowledgeThatCitesASource() {
		fixChatClientResponse(new SheetDraft(
			"Objets techniques",
			new SheetCourseContent(
				List.of(new SheetStatement("Ils répondent à un besoin.", List.of("C1"))),
				List.of(),
				List.of()),
			List.of(new SheetGeneralKnowledgeDraft("Un complément.", List.of("C1"))),
			List.of()));

		assertThrows(IllegalStateException.class,
			() -> sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY));
		assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM sheet", Integer.class));
	}

	@Test
	void refusesGenerationWhenNoCoursePassageIsFoundEvenIfReferenceExists() {
		jdbcTemplate.update("DELETE FROM vector_store");
		vectorStore.add(List.of(Document.builder()
			.text("Le programme étudie les objets techniques.")
			.metadata(Map.of(
				"origin", "OFFICIAL_REFERENCE",
				"subject", Subject.TECHNOLOGY.name(),
				"title", "Programme de technologie",
				"sourceUrl", "https://example.test/technology",
				"sourceFile", "programme.pdf",
				"chapter", "Objets techniques",
				"publicationDate", "2025-09-01"))
			.build()));

		assertThrows(NoCoursePassagesFoundException.class,
			() -> sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY));
		verify(chatClient, org.mockito.Mockito.never()).prompt();
		assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM sheet", Integer.class));
	}

	@Test
	void verifiesSheetAndPublishesEventOnlyOnce() {
		fixChatClientResponse(validDraft());
		Sheet sheet = sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY);

		sheetVerificationService.verify(sheet.id());

		assertEquals("VERIFIED",
			jdbcTemplate.queryForObject("SELECT status FROM sheet WHERE id = ?", String.class, sheet.id()));
		assertEquals(1, applicationEvents.stream(SheetVerified.class)
			.filter(event -> event.sheetId().equals(sheet.id())
				&& event.subject() == Subject.TECHNOLOGY
				&& event.theme().equals("Objets techniques"))
			.count());
		assertThrows(IllegalStateException.class, () -> sheetVerificationService.verify(sheet.id()));
		assertEquals(1, applicationEvents.stream(SheetVerified.class).count());
	}

	@Test
	void exportsSheetWithCitationsAndVerificationWarning() {
		fixChatClientResponse(validDraft());
		Sheet sheet = sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY);

		String unverifiedMarkdown = sheetMarkdownExportService.exportMarkdown(sheet.id());

		assertTrue(unverifiedMarkdown.contains("n'a pas encore été vérifiée"));
		assertTrue(unverifiedMarkdown.contains("**Statut :** À vérifier"));
		assertTrue(unverifiedMarkdown.contains("Ils répondent à un besoin. [C1]"));
		assertTrue(unverifiedMarkdown.contains("Objet technique"));
		assertTrue(unverifiedMarkdown.contains("Programme de technologie"));
		assertTrue(unverifiedMarkdown.contains("### Repères"));
		assertTrue(unverifiedMarkdown.contains("Un besoin est à l'origine de l'objet. [R1]"));
		assertTrue(unverifiedMarkdown.contains("## Points à compléter"));
		assertTrue(unverifiedMarkdown.contains("Le cours ne précise pas davantage ce thème."));
		assertTrue(unverifiedMarkdown.contains("technologie.docx"));
		assertTrue(unverifiedMarkdown.contains("Chapitre 1 - Objets techniques"));
		assertTrue(unverifiedMarkdown.contains("https://example.test/technology"));
		assertTrue(unverifiedMarkdown.contains("2025-09-01"));
		assertTrue(unverifiedMarkdown.contains("Complément de connaissances générales — à vérifier"));
		assertTrue(unverifiedMarkdown.contains("[GENERAL_KNOWLEDGE] — À vérifier"));
		assertTrue(unverifiedMarkdown.contains("Le rapport v = d / t — à vérifier."));
		assertFalse(unverifiedMarkdown.contains("Le rapport v = d / t — à vérifier. ["));

		sheetVerificationService.verify(sheet.id());
		String verifiedMarkdown = sheetMarkdownExportService.exportMarkdown(sheet.id());

		assertFalse(verifiedMarkdown.contains("n'a pas encore été vérifiée"));
		assertTrue(verifiedMarkdown.contains("**Statut :** Vérifiée"));
	}

	@Test
	void promptSeparatesGeneralKnowledgeAndSetsScienceRestrictions() throws Exception {
		fixChatClientResponse(validDraft());
		sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY);

		String common = new String(commonPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String variant = new String(genericPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String prompt = common + variant;
		assertTrue(prompt.contains("courseContent"));
		assertTrue(prompt.contains("generalKnowledge"));
		assertTrue(prompt.contains("GENERAL_KNOWLEDGE"));
		assertTrue(prompt.contains("ne présente jamais une connaissance générale comme venant du cours"));
		assertTrue(prompt.contains("n'invente aucun exemple numérique"));
		assertTrue(prompt.contains("Toute formule placée dans `generalKnowledge` doit être explicitement suivie de « à vérifier »"));
		assertTrue(common.contains("Les règles de citation et de séparation ci-dessus s'appliquent à tous les types"));
		assertTrue(variant.contains("courseContent.definitions"));
		assertEquals(1, suppliedPrompts.size());
		assertTrue(suppliedPrompts.getFirst().contains("Les règles de citation et de séparation"));
		assertTrue(suppliedPrompts.getFirst().contains("courseContent.definitions"));
	}

	@Test
	void rejectsTypeNotExplicitlyAvailableForTheSelectedSubject() {
		assertThrows(IllegalArgumentException.class,
			() -> sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY, "CHRONOLOGY"));
		verify(chatClient, org.mockito.Mockito.never()).prompt();
	}

	@Test
	void unknownTypeAndSchemaVersionAreReadableInDegradedMode() {
		UUID unknownTypeId = UUID.randomUUID();
		jdbcTemplate.update("""
			INSERT INTO sheet
				(id, subject, theme, sheet_type, schema_version, variant_data, key_concepts,
				 definitions, landmarks, general_knowledge, limitations, sources, status, generated_at)
			VALUES (?, 'TECHNOLOGY', 'Fiche inconnue', 'FUTURE_TYPE', 1, '{}'::jsonb, '[]'::jsonb,
				'[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'TO_VERIFY', now())
			""", unknownTypeId);

		Sheet unknownType = sheetQueryService.findById(unknownTypeId).orElseThrow();
		assertInstanceOf(UnavailableSheet.class, unknownType);
		assertTrue(unknownType.degradationMessage().contains("FUTURE_TYPE"));

		UUID oldSchemaId = UUID.randomUUID();
		jdbcTemplate.update("""
			INSERT INTO sheet
				(id, subject, theme, sheet_type, schema_version, variant_data, key_concepts,
				 definitions, landmarks, general_knowledge, limitations, sources, status, generated_at)
			VALUES (?, 'TECHNOLOGY', 'Ancienne fiche', 'GENERIC', 0,
				'{"definitions":[],"landmarks":[]}'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb,
				'[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'TO_VERIFY', now())
			""", oldSchemaId);

		Sheet oldSchema = sheetQueryService.findById(oldSchemaId).orElseThrow();
		assertInstanceOf(UnavailableSheet.class, oldSchema);
		assertTrue(oldSchema.degradationMessage().contains("version 0"));
	}

	@Test
	void unknownJsonPayloadIsReadableInDegradedMode() {
		UUID sheetId = UUID.randomUUID();
		jdbcTemplate.update("""
			INSERT INTO sheet
				(id, subject, theme, sheet_type, schema_version, variant_data, key_concepts,
				 definitions, landmarks, general_knowledge, limitations, sources, status, generated_at)
			VALUES (?, 'TECHNOLOGY', 'Fiche évolutive', 'GENERIC', 1,
				'{"definitions":[],"landmarks":[],"futureField":"ignored"}'::jsonb,
				'[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb,
				'TO_VERIFY', now())
			""", sheetId);

		Sheet sheet = sheetQueryService.findById(sheetId).orElseThrow();
		assertInstanceOf(UnavailableSheet.class, sheet);
		assertTrue(sheet.degradationMessage().contains("format JSON"));
	}

	@Test
	void rejectsVerificationAndExportForUnknownSheet() {
		java.util.UUID missingSheetId = java.util.UUID.randomUUID();

		assertThrows(IllegalArgumentException.class, () -> sheetVerificationService.verify(missingSheetId));
		assertThrows(IllegalArgumentException.class, () -> sheetMarkdownExportService.exportMarkdown(missingSheetId));
		assertEquals(0, applicationEvents.stream(SheetVerified.class).count());
	}

	private static SheetDraft validDraft() {
		return new SheetDraft(
			"Objets techniques",
			new SheetCourseContent(
				List.of(new SheetStatement("Ils répondent à un besoin.", List.of("C1"))),
				List.of(new SheetDefinition("Objet technique", "Objet conçu pour répondre à un besoin.", List.of("R1"))),
				List.of(new SheetStatement("Un besoin est à l'origine de l'objet.", List.of("R1")))),
			List.of(new SheetGeneralKnowledgeDraft("Le rapport v = d / t — à vérifier.", List.of())),
			List.of("Le cours ne précise pas davantage ce thème."));
	}

	private void fixChatClientResponse(SheetDraft response) {
		ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
		PromptSystemSpec systemSpec = mock(PromptSystemSpec.class);
		doAnswer(invocation -> {
			suppliedPrompts.add(invocation.getArgument(0, String.class));
			return systemSpec;
		}).when(systemSpec).text(anyString());
		when(systemSpec.param(anyString(), any())).thenReturn(systemSpec);
		when(chatClient.prompt()).thenReturn(request);
		doAnswer(invocation -> {
			Consumer<PromptSystemSpec> system = invocation.getArgument(0);
			system.accept(systemSpec);
			return request;
		}).when(request).system(
			org.mockito.ArgumentMatchers.<Consumer<PromptSystemSpec>>any());
		when(request.call()).thenReturn(call);
		when(call.entity(SheetDraft.class)).thenReturn(response);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeGenerationConfiguration {

		@Bean
		@Primary
		ChatClient fixedChatClient() {
			return mock(ChatClient.class);
		}

		@Bean
		@Primary
		EmbeddingModel fakeEmbeddingModel() {
			return new EmbeddingModel() {
				@Override
				public EmbeddingResponse call(EmbeddingRequest request) {
					return new EmbeddingResponse(Stream.generate(() -> new Embedding(fakeVector(), 0))
						.limit(request.getInstructions().size())
						.toList());
				}

				@Override
				public float[] embed(Document document) {
					return fakeVector();
				}

				@Override
				public int dimensions() {
					return 1024;
				}
			};
		}

		private static float[] fakeVector() {
			float[] vector = new float[1024];
			vector[0] = 1.0f;
			return vector;
		}

	}

}
