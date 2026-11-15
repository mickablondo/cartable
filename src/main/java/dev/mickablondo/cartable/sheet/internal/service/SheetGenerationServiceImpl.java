package dev.mickablondo.cartable.sheet.internal.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.mickablondo.cartable.course.CoursePassage;
import dev.mickablondo.cartable.course.CourseSearchService;
import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.reference.ReferencePassage;
import dev.mickablondo.cartable.reference.ReferenceSearchService;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetGenerationService;
import dev.mickablondo.cartable.sheet.SheetGenerated;
import dev.mickablondo.cartable.sheet.SheetSource;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.internal.model.SheetDraft;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class SheetGenerationServiceImpl implements SheetGenerationService {

	private final CourseSearchService courseSearchService;
	private final ReferenceSearchService referenceSearchService;
	private final ChatClient chatClient;
	private final Resource generationPrompt;
	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;
	private final ApplicationEventPublisher events;

	SheetGenerationServiceImpl(
			CourseSearchService courseSearchService,
			ReferenceSearchService referenceSearchService,
			ChatClient chatClient,
			@Value("classpath:prompts/sheet-generation.st") Resource generationPrompt,
			JdbcTemplate jdbcTemplate,
			JsonMapper jsonMapper,
			ApplicationEventPublisher events) {
		this.courseSearchService = courseSearchService;
		this.referenceSearchService = referenceSearchService;
		this.chatClient = chatClient;
		this.generationPrompt = generationPrompt;
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
		this.events = events;
	}

	@Override
	public Sheet generate(String theme, Subject subject) {
		if (theme == null || theme.isBlank()) {
			throw new IllegalArgumentException("Le thème est obligatoire.");
		}
		if (subject == null) {
			throw new IllegalArgumentException("La matière est obligatoire.");
		}

		List<CoursePassage> coursePassages = courseSearchService.search(theme, subject);
		List<ReferencePassage> referencePassages = referenceSearchService.search(theme, subject);
		Map<String, SheetSource> sourceCatalog = new LinkedHashMap<>();
		List<String> context = new ArrayList<>();
		addCoursePassages(coursePassages, sourceCatalog, context, subject);
		addReferencePassages(referencePassages, sourceCatalog, context, subject);

		SheetDraft draft = chatClient.prompt()
			.system(system -> system.text(generationPrompt)
				.param("subject", subjectLabel(subject))
				.param("theme", theme.strip())
				.param("passages", String.join("\n\n", context)))
			.call()
			.entity(SheetDraft.class);
		if (draft == null) {
			throw new IllegalStateException("Le modèle n'a retourné aucun contenu structuré pour la fiche.");
		}

		List<SheetSource> citedSources = validateAndResolveSources(draft, sourceCatalog);
		Instant generatedAt = Instant.now();
		Sheet sheet = new Sheet(
			UUID.randomUUID(),
			subject,
			requiredText(draft.theme(), "Le thème généré est vide."),
			requiredList(draft.keyConcepts(), "Les notions clés sont absentes."),
			requiredList(draft.definitions(), "Les définitions sont absentes."),
			requiredList(draft.landmarks(), "Les repères sont absents."),
			requiredList(draft.limitations(), "Les limites sont absentes."),
			citedSources,
			generatedAt);

		save(sheet);
		events.publishEvent(new SheetGenerated(sheet.id(), sheet.subject(), sheet.theme(), sheet.generatedAt()));
		return sheet;
	}

	private static void addCoursePassages(
			List<CoursePassage> passages,
			Map<String, SheetSource> sourceCatalog,
			List<String> context,
			Subject subject) {
		for (int index = 0; index < passages.size(); index++) {
			CoursePassage passage = passages.get(index);
			String id = "C" + (index + 1);
			sourceCatalog.put(id, new SheetSource(
				id, "COURSE", passage.sourceFile(), passage.chapter(), null, null, null));
			context.add("""
				[%s] Origine : cours ; matière : %s ; chapitre : %s ; fichier : %s
				Texte : %s
				""".formatted(id, subjectLabel(subject), passage.chapter(), passage.sourceFile(), passage.text()).strip());
		}
	}

	private static void addReferencePassages(
			List<ReferencePassage> passages,
			Map<String, SheetSource> sourceCatalog,
			List<String> context,
			Subject subject) {
		for (int index = 0; index < passages.size(); index++) {
			ReferencePassage passage = passages.get(index);
			String id = "R" + (index + 1);
			sourceCatalog.put(id, new SheetSource(
				id,
				"OFFICIAL_REFERENCE",
				passage.sourceFile(),
				passage.chapter(),
				passage.title(),
				passage.sourceUrl(),
				passage.publicationDate()));
			context.add("""
				[%s] Origine : référentiel officiel ; matière : %s ; titre : %s ; chapitre : %s ;
				fichier : %s ; URL : %s ; date de publication : %s
				Texte : %s
				""".formatted(
					id,
					subjectLabel(subject),
					passage.title(),
					passage.chapter(),
					passage.sourceFile(),
					passage.sourceUrl(),
					passage.publicationDate(),
					passage.text()).strip());
		}
	}

	private static List<SheetSource> validateAndResolveSources(
			SheetDraft draft, Map<String, SheetSource> sourceCatalog) {
		Map<String, SheetSource> citedSources = new LinkedHashMap<>();
		addCitations(draft.keyConcepts(), sourceCatalog, citedSources);
		addDefinitionCitations(draft.definitions(), sourceCatalog, citedSources);
		addCitations(draft.landmarks(), sourceCatalog, citedSources);
		if (citedSources.isEmpty()) {
			throw new IllegalStateException("La fiche est refusée : elle ne cite aucune source.");
		}
		return List.copyOf(citedSources.values());
	}

	private static void addCitations(
			List<SheetStatement> statements,
			Map<String, SheetSource> sourceCatalog,
			Map<String, SheetSource> citedSources) {
		for (SheetStatement statement : requiredList(statements, "Un élément de la fiche est absent.")) {
			requiredText(statement.text(), "Un élément de la fiche est vide.");
			addCitationIds(statement.sourceIds(), sourceCatalog, citedSources);
		}
	}

	private static void addDefinitionCitations(
			List<SheetDefinition> definitions,
			Map<String, SheetSource> sourceCatalog,
			Map<String, SheetSource> citedSources) {
		for (SheetDefinition definition : requiredList(definitions, "Une définition de la fiche est absente.")) {
			requiredText(definition.term(), "Un terme de définition est vide.");
			requiredText(definition.definition(), "Une définition est vide.");
			addCitationIds(definition.sourceIds(), sourceCatalog, citedSources);
		}
	}

	private static void addCitationIds(
			List<String> sourceIds,
			Map<String, SheetSource> sourceCatalog,
			Map<String, SheetSource> citedSources) {
		if (sourceIds == null || sourceIds.isEmpty()) {
			throw new IllegalStateException("La fiche est refusée : chaque élément doit citer au moins une source.");
		}
		for (String sourceId : sourceIds) {
			SheetSource source = sourceCatalog.get(sourceId);
			if (source == null) {
				throw new IllegalStateException(
					"La fiche est refusée : l'identifiant de source '" + sourceId + "' ne correspond à aucun passage fourni.");
			}
			citedSources.put(sourceId, source);
		}
	}

	private void save(Sheet sheet) {
		try {
			int updatedRows = jdbcTemplate.update("""
				INSERT INTO sheet
					(id, subject, theme, key_concepts, definitions, landmarks, limitations, sources, status, generated_at)
				VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
				""",
				sheet.id(),
				sheet.subject().name(),
				sheet.theme(),
				jsonMapper.writeValueAsString(sheet.keyConcepts()),
				jsonMapper.writeValueAsString(sheet.definitions()),
				jsonMapper.writeValueAsString(sheet.landmarks()),
				jsonMapper.writeValueAsString(sheet.limitations()),
				jsonMapper.writeValueAsString(sheet.sources()),
				sheet.status().name(),
				Timestamp.from(sheet.generatedAt()));
			if (updatedRows != 1) {
				throw new IllegalStateException("La fiche n'a pas été enregistrée en base.");
			}
		}
		catch (tools.jackson.core.JacksonException exception) {
			throw new IllegalStateException("Impossible d'enregistrer le contenu structuré de la fiche.", exception);
		}
	}

	private static String subjectLabel(Subject subject) {
		return switch (subject) {
			case MATHEMATICS -> "mathématiques";
			case FRENCH -> "français";
			case HISTORY_GEOGRAPHY -> "histoire-géographie";
			case PHYSICS_CHEMISTRY -> "sciences physiques";
			case LIFE_AND_EARTH_SCIENCES -> "SVT";
			case TECHNOLOGY -> "technologie";
		};
	}

	private static String requiredText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(message);
		}
		return value.strip();
	}

	private static <T> List<T> requiredList(List<T> values, String message) {
		if (values == null) {
			throw new IllegalStateException(message);
		}
		return List.copyOf(values);
	}

}
