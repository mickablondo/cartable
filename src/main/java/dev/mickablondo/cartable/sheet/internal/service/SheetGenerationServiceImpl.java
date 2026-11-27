package dev.mickablondo.cartable.sheet.internal.service;

import java.nio.charset.StandardCharsets;
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
import dev.mickablondo.cartable.sheet.GenericSheet;
import dev.mickablondo.cartable.sheet.HistoryGeographyCharacter;
import dev.mickablondo.cartable.sheet.HistoryGeographyEvent;
import dev.mickablondo.cartable.sheet.HistoryGeographyLandmark;
import dev.mickablondo.cartable.sheet.HistoryGeographySheet;
import dev.mickablondo.cartable.sheet.NoCoursePassagesFoundException;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetCourseContent;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetGenerationService;
import dev.mickablondo.cartable.sheet.SheetGeneralKnowledge;
import dev.mickablondo.cartable.sheet.SheetGenerated;
import dev.mickablondo.cartable.sheet.SheetKnowledgeOrigin;
import dev.mickablondo.cartable.sheet.SheetSource;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.SheetTypeOption;
import dev.mickablondo.cartable.sheet.internal.configuration.SheetVariantDefinition;
import dev.mickablondo.cartable.sheet.internal.configuration.SheetVariantRegistry;
import dev.mickablondo.cartable.sheet.internal.model.GenericSheetContent;
import dev.mickablondo.cartable.sheet.internal.model.HistoryGeographyCourseContent;
import dev.mickablondo.cartable.sheet.internal.model.HistoryGeographySheetContent;
import dev.mickablondo.cartable.sheet.internal.model.HistoryGeographySheetDraft;
import dev.mickablondo.cartable.sheet.internal.model.SheetDraft;
import dev.mickablondo.cartable.sheet.internal.model.SheetGeneralKnowledgeDraft;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class SheetGenerationServiceImpl implements SheetGenerationService {

	private final CourseSearchService courseSearchService;
	private final ReferenceSearchService referenceSearchService;
	private final ChatClient chatClient;
	private final Resource commonPrompt;
	private final ResourceLoader resourceLoader;
	private final SheetVariantRegistry variantRegistry;
	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;
	private final ApplicationEventPublisher events;

	SheetGenerationServiceImpl(
			CourseSearchService courseSearchService,
			ReferenceSearchService referenceSearchService,
			ChatClient chatClient,
			@Value("classpath:prompts/sheet-generation-common.st") Resource commonPrompt,
			ResourceLoader resourceLoader,
			SheetVariantRegistry variantRegistry,
			JdbcTemplate jdbcTemplate,
			JsonMapper jsonMapper,
			ApplicationEventPublisher events) {
		this.courseSearchService = courseSearchService;
		this.referenceSearchService = referenceSearchService;
		this.chatClient = chatClient;
		this.commonPrompt = commonPrompt;
		this.resourceLoader = resourceLoader;
		this.variantRegistry = variantRegistry;
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
		this.events = events;
	}

	@Override
	public Sheet generate(String theme, Subject subject) {
		return generate(theme, subject, null);
	}

	@Override
	public List<SheetTypeOption> availableTypes() {
		return variantRegistry.availableTypes();
	}

	@Override
	public Sheet generate(String theme, Subject subject, String requestedSheetType) {
		if (theme == null || theme.isBlank()) {
			throw new IllegalArgumentException("Le thème est obligatoire.");
		}
		if (subject == null) {
			throw new IllegalArgumentException("La matière est obligatoire.");
		}
		String sheetType = requestedSheetType == null || requestedSheetType.isBlank()
			? variantRegistry.defaultType(subject)
			: requestedSheetType.strip();
		SheetVariantDefinition variant = variantRegistry.require(subject, sheetType);

		List<CoursePassage> coursePassages = courseSearchService.search(theme, subject);
		if (coursePassages.isEmpty()) {
			throw new NoCoursePassagesFoundException();
		}
		List<ReferencePassage> referencePassages = referenceSearchService.search(theme, subject);
		Map<String, SheetSource> sourceCatalog = new LinkedHashMap<>();
		Map<String, String> sourcePassages = new LinkedHashMap<>();
		List<String> context = new ArrayList<>();
		addCoursePassages(coursePassages, sourceCatalog, sourcePassages, context, subject);
		addReferencePassages(referencePassages, sourceCatalog, sourcePassages, context, subject);

		String prompt = readPrompt(commonPrompt) + "\n\n" + readPrompt(resourceLoader.getResource(variant.promptResource()));
		if ("HISTORY_GEOGRAPHY".equals(variant.sheetType())) {
			return generateHistoryGeography(
				theme, subject, variant, prompt, sourceCatalog, sourcePassages, context);
		}
		SheetDraft draft = chatClient.prompt()
			.system(system -> system.text(prompt)
				.param("subject", subjectLabel(subject))
				.param("theme", theme.strip())
				.param("passages", String.join("\n\n", context)))
			.call()
			.entity(SheetDraft.class);
		if (draft == null) {
			throw new IllegalStateException("Le modèle n'a retourné aucun contenu structuré pour la fiche.");
		}

		SheetCourseContent courseContent = requiredValue(
			draft.courseContent(), "Le contenu issu des sources est absent.");
		List<SheetSource> citedSources = validateAndResolveSources(courseContent, sourceCatalog);
		List<SheetGeneralKnowledge> generalKnowledge = validateGeneralKnowledge(draft.generalKnowledge());
		GenericSheetContent variantContent = new GenericSheetContent(
			courseContent.definitions(), courseContent.landmarks());
		Instant generatedAt = Instant.now();
		Sheet sheet = new GenericSheet(
			UUID.randomUUID(),
			subject,
			requiredText(draft.theme(), "Le thème généré est vide."),
			variant.sheetType(),
			variant.schemaVersion(),
			courseContent.keyConcepts(),
			variantContent.definitions(),
			variantContent.landmarks(),
			generalKnowledge,
			requiredList(draft.limitations(), "Les limites sont absentes."),
			citedSources,
			dev.mickablondo.cartable.sheet.SheetStatus.TO_VERIFY,
			generatedAt);

		save(sheet, variantContent);
		events.publishEvent(new SheetGenerated(sheet.id(), sheet.subject(), sheet.theme(), sheet.generatedAt()));
		return sheet;
	}

	private static void addCoursePassages(
			List<CoursePassage> passages,
			Map<String, SheetSource> sourceCatalog,
			Map<String, String> sourcePassages,
			List<String> context,
			Subject subject) {
		for (int index = 0; index < passages.size(); index++) {
			CoursePassage passage = passages.get(index);
			String id = "C" + (index + 1);
			sourceCatalog.put(id, new SheetSource(
				id, "COURSE", passage.sourceFile(), passage.chapter(), null, null, null));
			sourcePassages.put(id, passage.text());
			context.add("""
				[%s] Origine : cours ; matière : %s ; chapitre : %s ; fichier : %s
				Texte : %s
				""".formatted(id, subjectLabel(subject), passage.chapter(), passage.sourceFile(), passage.text()).strip());
		}
	}

	private static void addReferencePassages(
			List<ReferencePassage> passages,
			Map<String, SheetSource> sourceCatalog,
			Map<String, String> sourcePassages,
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
			sourcePassages.put(id, passage.text());
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
			SheetCourseContent courseContent, Map<String, SheetSource> sourceCatalog) {
		Map<String, SheetSource> citedSources = new LinkedHashMap<>();
		addCitations(courseContent.keyConcepts(), sourceCatalog, citedSources);
		addDefinitionCitations(courseContent.definitions(), sourceCatalog, citedSources);
		addCitations(courseContent.landmarks(), sourceCatalog, citedSources);
		if (citedSources.isEmpty()) {
			throw new IllegalStateException("La fiche est refusée : elle ne cite aucune source.");
		}
		return List.copyOf(citedSources.values());
	}

	private Sheet generateHistoryGeography(
			String theme,
			Subject subject,
			SheetVariantDefinition variant,
			String prompt,
			Map<String, SheetSource> sourceCatalog,
			Map<String, String> sourcePassages,
			List<String> context) {
		HistoryGeographySheetDraft draft = chatClient.prompt()
			.system(system -> system.text(prompt)
				.param("subject", subjectLabel(subject))
				.param("theme", theme.strip())
				.param("passages", String.join("\n\n", context)))
			.call()
			.entity(HistoryGeographySheetDraft.class);
		if (draft == null) {
			throw new IllegalStateException("Le modèle n'a retourné aucun contenu structuré pour la fiche.");
		}

		HistoryGeographyCourseContent courseContent = requiredValue(
			draft.courseContent(), "Le contenu issu des sources est absent.");
		List<SheetSource> citedSources = validateAndResolveHistoryGeographySources(
			courseContent, sourceCatalog, sourcePassages);
		List<SheetGeneralKnowledge> generalKnowledge = validateGeneralKnowledge(draft.generalKnowledge());
		Instant generatedAt = Instant.now();
		HistoryGeographySheet sheet = new HistoryGeographySheet(
			UUID.randomUUID(),
			subject,
			requiredText(draft.theme(), "Le thème généré est vide."),
			variant.sheetType(),
			variant.schemaVersion(),
			requiredList(courseContent.keyConcepts(), "Les notions clés sont absentes."),
			requiredList(courseContent.chronology(), "La chronologie est absente."),
			requiredList(courseContent.characters(), "Les personnages sont absents."),
			requiredList(courseContent.definitions(), "Les notions à définir sont absentes."),
			requiredList(courseContent.spatialLandmarks(), "Les repères spatiaux sont absents."),
			generalKnowledge,
			requiredList(draft.limitations(), "Les limites sont absentes."),
			citedSources,
			dev.mickablondo.cartable.sheet.SheetStatus.TO_VERIFY,
			generatedAt);
		HistoryGeographySheetContent variantContent = new HistoryGeographySheetContent(
			sheet.chronology(),
			sheet.characters(),
			sheet.definitions(),
			sheet.spatialLandmarks());

		save(sheet, variantContent);
		events.publishEvent(new SheetGenerated(sheet.id(), sheet.subject(), sheet.theme(), sheet.generatedAt()));
		return sheet;
	}

	private static List<SheetSource> validateAndResolveHistoryGeographySources(
			HistoryGeographyCourseContent courseContent,
			Map<String, SheetSource> sourceCatalog,
			Map<String, String> sourcePassages) {
		Map<String, SheetSource> citedSources = new LinkedHashMap<>();
		addCitations(courseContent.keyConcepts(), sourceCatalog, citedSources);
		addDefinitionCitations(courseContent.definitions(), sourceCatalog, citedSources);

		for (HistoryGeographyEvent event : requiredList(
				courseContent.chronology(), "La chronologie est absente.")) {
			String date = requiredText(event.date(), "Une date de la chronologie est vide.");
			requiredText(event.event(), "Un événement de la chronologie est vide.");
			addCitationIds(event.sourceIds(), sourceCatalog, citedSources);
			boolean dateIsCited = event.sourceIds().stream()
				.map(sourcePassages::get)
				.filter(java.util.Objects::nonNull)
				.anyMatch(passage -> passage.contains(date));
			if (!dateIsCited) {
				throw new IllegalStateException(
					"La fiche est refusée : la date « " + date + " » n'apparaît dans aucun des passages cités.");
			}
		}
		for (HistoryGeographyCharacter character : requiredList(
				courseContent.characters(), "Les personnages sont absents.")) {
			requiredText(character.name(), "Un nom de personnage est vide.");
			requiredText(character.role(), "Le rôle d'un personnage est vide.");
			addCitationIds(character.sourceIds(), sourceCatalog, citedSources);
		}
		for (HistoryGeographyLandmark landmark : requiredList(
				courseContent.spatialLandmarks(), "Les repères spatiaux sont absents.")) {
			requiredText(landmark.place(), "Un lieu est vide.");
			addCitationIds(landmark.sourceIds(), sourceCatalog, citedSources);
		}
		if (citedSources.isEmpty()) {
			throw new IllegalStateException("La fiche est refusée : elle ne cite aucune source.");
		}
		return List.copyOf(citedSources.values());
	}

	private static List<SheetGeneralKnowledge> validateGeneralKnowledge(
			List<SheetGeneralKnowledgeDraft> generalKnowledge) {
		List<SheetGeneralKnowledge> validated = new ArrayList<>();
		for (SheetGeneralKnowledgeDraft item : requiredList(
				generalKnowledge, "Le complément de connaissances générales est absent.")) {
			requiredText(item.text(), "Un élément du complément de connaissances générales est vide.");
			if (item.sourceIds() != null && !item.sourceIds().isEmpty()) {
				throw new IllegalStateException(
					"Le complément de connaissances générales ne peut pas citer de source.");
			}
			validated.add(new SheetGeneralKnowledge(item.text().strip(), SheetKnowledgeOrigin.GENERAL_KNOWLEDGE));
		}
		return List.copyOf(validated);
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

	private void save(Sheet sheet, Object variantContent) {
		List<SheetDefinition> legacyDefinitions = variantContent instanceof GenericSheetContent genericContent
			? genericContent.definitions()
			: List.of();
		List<SheetStatement> legacyLandmarks = variantContent instanceof GenericSheetContent genericContent
			? genericContent.landmarks()
			: List.of();
		try {
			int updatedRows = jdbcTemplate.update("""
				INSERT INTO sheet
					(id, subject, theme, sheet_type, schema_version, variant_data, key_concepts,
					 definitions, landmarks, general_knowledge, limitations, sources, status, generated_at)
				VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb,
					?::jsonb, ?, ?)
				""",
				sheet.id(),
				sheet.subject().name(),
				sheet.theme(),
				sheet.sheetType(),
				sheet.schemaVersion(),
				jsonMapper.writeValueAsString(variantContent),
				jsonMapper.writeValueAsString(sheet.keyConcepts()),
				jsonMapper.writeValueAsString(legacyDefinitions),
				jsonMapper.writeValueAsString(legacyLandmarks),
				jsonMapper.writeValueAsString(sheet.generalKnowledge()),
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

	private static String readPrompt(Resource resource) {
		try (var input = resource.getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (java.io.IOException exception) {
			throw new IllegalStateException("Impossible de lire le prompt de génération de fiche.", exception);
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

	private static <T> T requiredValue(T value, String message) {
		if (value == null) {
			throw new IllegalStateException(message);
		}
		return value;
	}

	private static <T> List<T> requiredList(List<T> values, String message) {
		if (values == null) {
			throw new IllegalStateException(message);
		}
		return List.copyOf(values);
	}

}
