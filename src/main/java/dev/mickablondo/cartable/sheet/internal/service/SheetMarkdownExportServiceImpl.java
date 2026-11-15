package dev.mickablondo.cartable.sheet.internal.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetMarkdownExportService;
import dev.mickablondo.cartable.sheet.SheetSource;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.SheetStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class SheetMarkdownExportServiceImpl implements SheetMarkdownExportService {

	private static final TypeReference<List<SheetStatement>> STATEMENTS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<SheetDefinition>> DEFINITIONS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<String>> STRINGS_TYPE = new TypeReference<>() {
	};
	private static final TypeReference<List<SheetSource>> SOURCES_TYPE = new TypeReference<>() {
	};

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	SheetMarkdownExportServiceImpl(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public String exportMarkdown(UUID sheetId) {
		if (sheetId == null) {
			throw new IllegalArgumentException("L'identifiant de la fiche est obligatoire.");
		}

		List<Sheet> sheets = jdbcTemplate.query("""
			SELECT id, subject, theme, key_concepts, definitions, landmarks, limitations, sources, status, generated_at
			FROM sheet
			WHERE id = ?
			""",
			this::toSheet,
			sheetId);
		if (sheets.isEmpty()) {
			throw new IllegalArgumentException("Aucune fiche ne correspond à cet identifiant.");
		}
		return render(sheets.getFirst());
	}

	private static String render(Sheet sheet) {
		StringBuilder markdown = new StringBuilder("# ").append(sheet.theme()).append("\n\n");
		if (sheet.status() == SheetStatus.TO_VERIFY) {
			markdown.append("> **⚠️ Cette fiche n'a pas encore été vérifiée. Vérifiez son contenu avant de l'utiliser.**\n\n");
		}
		markdown.append("**Matière :** ").append(subjectLabel(sheet.subject())).append("  \n")
			.append("**Statut :** ").append(sheet.status() == SheetStatus.VERIFIED ? "Vérifiée" : "À vérifier")
			.append("\n\n");

		Map<String, SheetSource> sources = indexSources(sheet.sources());
		appendStatements(markdown, "Notions clés", sheet.keyConcepts(), sources);
		appendDefinitions(markdown, sheet.definitions(), sources);
		appendStatements(markdown, "Repères", sheet.landmarks(), sources);
		appendLimitations(markdown, sheet.limitations());
		appendSources(markdown, sheet.sources());
		return markdown.toString();
	}

	private static void appendStatements(
			StringBuilder markdown,
			String heading,
			List<SheetStatement> statements,
			Map<String, SheetSource> sources) {
		if (statements.isEmpty()) {
			return;
		}
		markdown.append("## ").append(heading).append("\n\n");
		for (SheetStatement statement : statements) {
			markdown.append("- ").append(requiredText(statement.text()))
				.append(" ").append(citations(statement.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendDefinitions(
			StringBuilder markdown,
			List<SheetDefinition> definitions,
			Map<String, SheetSource> sources) {
		if (definitions.isEmpty()) {
			return;
		}
		markdown.append("## Définitions\n\n");
		for (SheetDefinition definition : definitions) {
			markdown.append("- **").append(requiredText(definition.term())).append("** — ")
				.append(requiredText(definition.definition())).append(" ")
				.append(citations(definition.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendLimitations(StringBuilder markdown, List<String> limitations) {
		if (limitations.isEmpty()) {
			return;
		}
		markdown.append("## Points à compléter\n\n");
		for (String limitation : limitations) {
			markdown.append("- ").append(requiredText(limitation)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendSources(StringBuilder markdown, List<SheetSource> sourceList) {
		if (sourceList.isEmpty()) {
			throw new IllegalStateException("La fiche ne contient aucune source à exporter.");
		}
		markdown.append("## Sources\n\n");
		for (SheetSource source : sourceList) {
			markdown.append("- **[").append(requiredText(source.id())).append("]** ");
			String origin = requiredText(source.origin());
			if (origin.equals("COURSE")) {
				markdown.append("Cours — fichier `").append(requiredText(source.sourceFile()))
					.append("`, chapitre « ").append(requiredText(source.chapter())).append(" ».");
			}
			else if (origin.equals("OFFICIAL_REFERENCE")) {
				markdown.append("Référentiel officiel — *").append(requiredText(source.title())).append("*");
				if (source.sourceFile() != null && !source.sourceFile().isBlank()) {
					markdown.append(", fichier `").append(source.sourceFile()).append('`');
				}
				if (source.publicationDate() == null) {
					throw new IllegalStateException("Une référence officielle ne contient pas sa date de publication.");
				}
				markdown.append(", chapitre « ").append(requiredText(source.chapter()))
					.append(" », publié le ").append(source.publicationDate())
					.append(". URL : ").append(requiredText(source.sourceUrl())).append('.');
			}
			else {
				throw new IllegalStateException("Origine inconnue pour la source " + source.id() + ".");
			}
			markdown.append('\n');
		}
	}

	private static Map<String, SheetSource> indexSources(List<SheetSource> sourceList) {
		Map<String, SheetSource> sources = new LinkedHashMap<>();
		for (SheetSource source : sourceList) {
			if (sources.putIfAbsent(requiredText(source.id()), source) != null) {
				throw new IllegalStateException("La source " + source.id() + " est déclarée plusieurs fois.");
			}
		}
		return sources;
	}

	private static String citations(List<String> sourceIds, Map<String, SheetSource> sources) {
		if (sourceIds == null || sourceIds.isEmpty()) {
			throw new IllegalStateException("Un élément de la fiche ne cite aucune source.");
		}
		return sourceIds.stream()
			.map(sourceId -> {
				if (!sources.containsKey(sourceId)) {
					throw new IllegalStateException("La source citée " + sourceId + " est absente de la fiche.");
				}
				return "[" + sourceId + "]";
			})
			.collect(Collectors.joining(" "));
	}

	private static String requiredText(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("La fiche contient un texte vide.");
		}
		return value.strip();
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

	private Sheet toSheet(ResultSet resultSet, int rowNumber) throws SQLException {
		try {
			return new Sheet(
				resultSet.getObject("id", UUID.class),
				Subject.valueOf(resultSet.getString("subject")),
				resultSet.getString("theme"),
				jsonMapper.readValue(resultSet.getString("key_concepts"), STATEMENTS_TYPE),
				jsonMapper.readValue(resultSet.getString("definitions"), DEFINITIONS_TYPE),
				jsonMapper.readValue(resultSet.getString("landmarks"), STATEMENTS_TYPE),
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
