package dev.mickablondo.cartable.sheet.internal.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetCourseContent;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetGeneralKnowledge;
import dev.mickablondo.cartable.sheet.SheetQueryService;
import dev.mickablondo.cartable.sheet.SheetSource;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.SheetStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class SheetQueryServiceImpl implements SheetQueryService {

	private static final TypeReference<List<SheetStatement>> STATEMENTS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<SheetDefinition>> DEFINITIONS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<String>> STRINGS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<SheetSource>> SOURCES_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<SheetGeneralKnowledge>> GENERAL_KNOWLEDGE_TYPE = new TypeReference<>() {
	};

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
			SELECT id, subject, theme, key_concepts, definitions, landmarks, general_knowledge, limitations,
			       sources, status, generated_at
			FROM sheet
			WHERE id = ?
			""",
			this::toSheet,
			sheetId);
		return sheets.stream().findFirst();
	}

	private Sheet toSheet(ResultSet resultSet, int rowNumber) throws SQLException {
		try {
			return new Sheet(
				resultSet.getObject("id", UUID.class),
				Subject.valueOf(resultSet.getString("subject")),
				resultSet.getString("theme"),
				new SheetCourseContent(
					jsonMapper.readValue(resultSet.getString("key_concepts"), STATEMENTS_TYPE),
					jsonMapper.readValue(resultSet.getString("definitions"), DEFINITIONS_TYPE),
					jsonMapper.readValue(resultSet.getString("landmarks"), STATEMENTS_TYPE)),
				jsonMapper.readValue(resultSet.getString("general_knowledge"), GENERAL_KNOWLEDGE_TYPE),
				jsonMapper.readValue(resultSet.getString("limitations"), STRINGS_TYPE),
				jsonMapper.readValue(resultSet.getString("sources"), SOURCES_TYPE),
				SheetStatus.valueOf(resultSet.getString("status")),
				resultSet.getTimestamp("generated_at").toInstant());
		}
		catch (JacksonException exception) {
			throw new SQLException("Impossible de lire le contenu structuré de la fiche.", exception);
		}
	}

}
