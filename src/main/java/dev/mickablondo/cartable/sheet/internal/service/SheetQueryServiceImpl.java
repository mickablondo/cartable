package dev.mickablondo.cartable.sheet.internal.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.GenericSheet;
import dev.mickablondo.cartable.sheet.HistoryGeographySheet;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetGeneralKnowledge;
import dev.mickablondo.cartable.sheet.SheetQueryService;
import dev.mickablondo.cartable.sheet.SheetSource;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.SheetStatus;
import dev.mickablondo.cartable.sheet.UnavailableSheet;
import dev.mickablondo.cartable.sheet.internal.model.GenericSheetContent;
import dev.mickablondo.cartable.sheet.internal.model.HistoryGeographySheetContent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class SheetQueryServiceImpl implements SheetQueryService {

	private static final TypeReference<List<SheetStatement>> STATEMENTS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<String>> STRINGS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<SheetSource>> SOURCES_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<SheetGeneralKnowledge>> GENERAL_KNOWLEDGE_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<Map<String, Object>> VARIANT_FIELDS_TYPE = new TypeReference<>() {
	};
	private static final Set<String> GENERIC_FIELDS = Set.of("definitions", "landmarks");
	private static final Set<String> HISTORY_GEOGRAPHY_FIELDS =
		Set.of("chronology", "characters", "definitions", "spatialLandmarks");
	private static final String GENERIC_TYPE = "GENERIC";
	private static final String HISTORY_GEOGRAPHY_TYPE = "HISTORY_GEOGRAPHY";
	private static final int GENERIC_SCHEMA_VERSION = 1;
	private static final int HISTORY_GEOGRAPHY_SCHEMA_VERSION = 1;

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	SheetQueryServiceImpl(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Optional<Sheet> findById(UUID sheetId) {
		if (sheetId == null) {
			throw new IllegalArgumentException("L'identifiant de la fiche est obligatoire.");
		}

		List<Sheet> sheets = jdbcTemplate.query("""
			SELECT id, subject, theme, sheet_type, schema_version, variant_data, key_concepts,
			       general_knowledge, limitations, sources, status, generated_at
			FROM sheet
			WHERE id = ?
			""",
			this::toSheet,
			sheetId);
		return sheets.stream().findFirst();
	}

	private Sheet toSheet(ResultSet resultSet, int rowNumber) throws SQLException {
		try {
			UUID id = resultSet.getObject("id", UUID.class);
			Subject subject = Subject.valueOf(resultSet.getString("subject"));
			String theme = resultSet.getString("theme");
			String sheetType = resultSet.getString("sheet_type");
			int schemaVersion = resultSet.getInt("schema_version");
			List<SheetStatement> keyConcepts = jsonMapper.readValue(
				resultSet.getString("key_concepts"), STATEMENTS_TYPE);
			List<SheetGeneralKnowledge> generalKnowledge = jsonMapper.readValue(
				resultSet.getString("general_knowledge"), GENERAL_KNOWLEDGE_TYPE);
			List<String> limitations = jsonMapper.readValue(resultSet.getString("limitations"), STRINGS_TYPE);
			List<SheetSource> sources = jsonMapper.readValue(resultSet.getString("sources"), SOURCES_TYPE);
			SheetStatus status = SheetStatus.valueOf(resultSet.getString("status"));
			var generatedAt = resultSet.getTimestamp("generated_at").toInstant();

			if (HISTORY_GEOGRAPHY_TYPE.equals(sheetType)) {
				if (schemaVersion != HISTORY_GEOGRAPHY_SCHEMA_VERSION) {
					return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
						limitations, sources, status, generatedAt, "La structure de cette fiche n'est pas prise en charge "
							+ "(version " + schemaVersion + ").");
				}
				return historyGeographySheet(
					resultSet.getString("variant_data"), id, subject, theme, sheetType, schemaVersion,
					keyConcepts, generalKnowledge, limitations, sources, status, generatedAt);
			}
			if (!GENERIC_TYPE.equals(sheetType)) {
				return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
					limitations, sources, status, generatedAt, "Le type de fiche « " + sheetType
						+ " » n'est pas pris en charge par cette version de l'application.");
			}
			if (schemaVersion != GENERIC_SCHEMA_VERSION) {
				return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
					limitations, sources, status, generatedAt, "La structure de cette fiche n'est pas prise en charge "
						+ "(version " + schemaVersion + ").");
			}

			GenericSheetContent content;
			try {
				String variantData = resultSet.getString("variant_data");
				Map<String, Object> variantFields = jsonMapper.readValue(variantData, VARIANT_FIELDS_TYPE);
				if (variantFields == null || !variantFields.keySet().equals(GENERIC_FIELDS)) {
					return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
						limitations, sources, status, generatedAt,
						"Le format JSON de cette fiche n'est pas reconnu.");
				}
				content = jsonMapper.readValue(variantData, GenericSheetContent.class);
			}
			catch (JacksonException exception) {
				return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
					limitations, sources, status, generatedAt, "Le contenu détaillé de cette fiche est illisible.");
			}
			if (content == null || content.definitions() == null || content.landmarks() == null) {
				return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
					limitations, sources, status, generatedAt, "Le contenu détaillé de cette fiche est incomplet.");
			}
			return new GenericSheet(
				id, subject, theme, sheetType, schemaVersion, keyConcepts,
				content.definitions(), content.landmarks(), generalKnowledge, limitations, sources, status, generatedAt);
		}
		catch (JacksonException exception) {
			throw new SQLException("Impossible de lire le contenu structuré de la fiche.", exception);
		}
	}

	private Sheet historyGeographySheet(
			String variantData,
			UUID id,
			Subject subject,
			String theme,
			String sheetType,
			int schemaVersion,
			List<SheetStatement> keyConcepts,
			List<SheetGeneralKnowledge> generalKnowledge,
			List<String> limitations,
			List<SheetSource> sources,
			SheetStatus status,
			java.time.Instant generatedAt) {
		try {
			Map<String, Object> variantFields = jsonMapper.readValue(variantData, VARIANT_FIELDS_TYPE);
			if (variantFields == null || !variantFields.keySet().equals(HISTORY_GEOGRAPHY_FIELDS)) {
				return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
					limitations, sources, status, generatedAt, "Le format JSON de cette fiche n'est pas reconnu.");
			}
			HistoryGeographySheetContent content = jsonMapper.readValue(variantData, HistoryGeographySheetContent.class);
			if (content == null || content.chronology() == null || content.characters() == null
					|| content.definitions() == null || content.spatialLandmarks() == null) {
				return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
					limitations, sources, status, generatedAt, "Le contenu détaillé de cette fiche est incomplet.");
			}
			return new HistoryGeographySheet(
				id, subject, theme, sheetType, schemaVersion, keyConcepts,
				content.chronology(), content.characters(), content.definitions(), content.spatialLandmarks(),
				generalKnowledge, limitations, sources, status, generatedAt);
		}
		catch (JacksonException exception) {
			return unavailable(id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge,
				limitations, sources, status, generatedAt, "Le contenu détaillé de cette fiche est illisible.");
		}
	}

	private static UnavailableSheet unavailable(
			UUID id,
			Subject subject,
			String theme,
			String sheetType,
			int schemaVersion,
			List<SheetStatement> keyConcepts,
			List<SheetGeneralKnowledge> generalKnowledge,
			List<String> limitations,
			List<SheetSource> sources,
			SheetStatus status,
			java.time.Instant generatedAt,
			String message) {
		return new UnavailableSheet(
			id, subject, theme, sheetType, schemaVersion, keyConcepts, generalKnowledge, limitations,
			sources, status, generatedAt, message);
	}

}
