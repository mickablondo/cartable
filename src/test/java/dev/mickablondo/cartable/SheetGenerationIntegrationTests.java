package dev.mickablondo.cartable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetGenerated;
import dev.mickablondo.cartable.sheet.SheetGenerationService;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.internal.model.SheetDraft;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Import({ TestcontainersConfiguration.class, SheetGenerationIntegrationTests.FakeGenerationConfiguration.class })
@RecordApplicationEvents
@SpringBootTest
class SheetGenerationIntegrationTests {

	@Autowired
	private SheetGenerationService sheetGenerationService;

	@Autowired
	private ChatClient chatClient;

	@Autowired
	private VectorStore vectorStore;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationEvents applicationEvents;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM sheet");
		jdbcTemplate.update("DELETE FROM vector_store");
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
			List.of(new SheetStatement("Ils répondent à un besoin.", List.of("C1"))),
			List.of(new SheetDefinition("Objet technique", "Objet conçu pour répondre à un besoin.", List.of("R1"))),
			List.of(),
			List.of()));

		Sheet sheet = sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY);

		assertEquals(Subject.TECHNOLOGY, sheet.subject());
		assertEquals("Objets techniques", sheet.theme());
		assertEquals("TO_VERIFY", sheet.status().name());
		assertEquals(2, sheet.sources().size());
		assertEquals("technologie.docx", sheet.sources().getFirst().sourceFile());
		assertEquals("https://example.test/technology", sheet.sources().get(1).sourceUrl());
		assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM sheet WHERE id = ?", Integer.class, sheet.id()));

		Map<String, Object> stored = jdbcTemplate.queryForMap("""
			SELECT subject, status, key_concepts -> 0 -> 'sourceIds' ->> 0 AS concept_source,
			       definitions -> 0 -> 'sourceIds' ->> 0 AS definition_source,
			       jsonb_array_length(sources) AS source_count
			FROM sheet
			WHERE id = ?
			""", sheet.id());
		assertEquals("TECHNOLOGY", stored.get("subject"));
		assertEquals("TO_VERIFY", stored.get("status"));
		assertEquals("C1", stored.get("concept_source"));
		assertEquals("R1", stored.get("definition_source"));
		assertEquals(2, stored.get("source_count"));
		assertEquals(1, applicationEvents.stream(SheetGenerated.class)
			.filter(event -> event.sheetId().equals(sheet.id()))
			.count());
	}

	@Test
	void refusesSheetWithoutCitedSources() {
		fixChatClientResponse(new SheetDraft(
			"Objets techniques",
			List.of(new SheetStatement("Ils répondent à un besoin.", List.of())),
			List.of(),
			List.of(),
			List.of("Le référentiel ne précise pas davantage ce thème.")));

		assertThrows(IllegalStateException.class,
			() -> sheetGenerationService.generate("Objets techniques", Subject.TECHNOLOGY));

		assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM sheet", Integer.class));
		assertEquals(0, applicationEvents.stream(SheetGenerated.class).count());
	}

	private void fixChatClientResponse(SheetDraft response) {
		ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
		PromptSystemSpec systemSpec = mock(PromptSystemSpec.class);
		when(systemSpec.text(any(Resource.class))).thenReturn(systemSpec);
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
