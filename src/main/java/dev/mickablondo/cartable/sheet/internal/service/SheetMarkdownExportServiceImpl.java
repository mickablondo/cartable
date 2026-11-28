package dev.mickablondo.cartable.sheet.internal.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.GenericSheet;
import dev.mickablondo.cartable.sheet.HistoryGeographyCharacter;
import dev.mickablondo.cartable.sheet.HistoryGeographyEvent;
import dev.mickablondo.cartable.sheet.HistoryGeographyLandmark;
import dev.mickablondo.cartable.sheet.HistoryGeographySheet;
import dev.mickablondo.cartable.sheet.MathCommonError;
import dev.mickablondo.cartable.sheet.MathExample;
import dev.mickablondo.cartable.sheet.MathFormula;
import dev.mickablondo.cartable.sheet.MathMethod;
import dev.mickablondo.cartable.sheet.MathProperty;
import dev.mickablondo.cartable.sheet.MathSheet;
import dev.mickablondo.cartable.sheet.MathTheorem;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetGeneralKnowledge;
import dev.mickablondo.cartable.sheet.SheetMarkdownExportService;
import dev.mickablondo.cartable.sheet.SheetQueryService;
import dev.mickablondo.cartable.sheet.SheetSource;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.SheetStatus;
import org.springframework.stereotype.Service;

@Service
class SheetMarkdownExportServiceImpl implements SheetMarkdownExportService {

	private final SheetQueryService sheetQueryService;

	SheetMarkdownExportServiceImpl(SheetQueryService sheetQueryService) {
		this.sheetQueryService = sheetQueryService;
	}

	@Override
	public String exportMarkdown(UUID sheetId) {
		if (sheetId == null) {
			throw new IllegalArgumentException("L'identifiant de la fiche est obligatoire.");
		}

		Sheet sheet = sheetQueryService.findById(sheetId)
			.orElseThrow(() -> new IllegalArgumentException("Aucune fiche ne correspond à cet identifiant."));
		return render(sheet);
	}

	private static String render(Sheet sheet) {
		StringBuilder markdown = new StringBuilder("# ").append(sheet.theme()).append("\n\n");
		if (sheet.status() == SheetStatus.TO_VERIFY) {
			markdown.append("> **⚠️ Cette fiche n'a pas encore été vérifiée. Vérifiez son contenu avant de l'utiliser.**\n\n");
		}
		markdown.append("**Matière :** ").append(subjectLabel(sheet.subject())).append("  \n")
			.append("**Type de fiche :** ").append(sheet.sheetType()).append("  \n")
			.append("**Statut :** ").append(sheet.status() == SheetStatus.VERIFIED ? "Vérifiée" : "À vérifier")
			.append("\n\n");

		Map<String, SheetSource> sources = indexSources(sheet.sources());
		appendCourseContent(markdown, sheet, sources);
		if (sheet.degradationMessage() != null) {
			markdown.append("## Contenu partiellement indisponible\n\n")
				.append(sheet.degradationMessage()).append("\n\n");
		}
		appendGeneralKnowledge(markdown, sheet.generalKnowledge());
		appendLimitations(markdown, sheet.limitations());
		appendSources(markdown, sheet.sources());
		return markdown.toString();
	}

	private static void appendCourseContent(
			StringBuilder markdown, Sheet sheet, Map<String, SheetSource> sources) {
			if (sheet instanceof MathSheet mathSheet) {
				appendMathContent(markdown, mathSheet, sources);
				return;
			}
			if (sheet instanceof HistoryGeographySheet historyGeographySheet) {
			appendHistoryGeographyContent(markdown, historyGeographySheet, sources);
			return;
		}
		List<SheetDefinition> definitions = sheet instanceof GenericSheet genericSheet
			? genericSheet.definitions()
			: List.of();
		List<SheetStatement> landmarks = sheet instanceof GenericSheet genericSheet
			? genericSheet.landmarks()
			: List.of();
		if (sheet.keyConcepts().isEmpty() && definitions.isEmpty() && landmarks.isEmpty()) {
			return;
		}
		markdown.append("## Contenu issu du cours et du référentiel — avec sources\n\n");
		appendStatements(markdown, "### Notions clés", sheet.keyConcepts(), sources);
		appendDefinitions(markdown, definitions, sources);
		appendStatements(markdown, "### Repères", landmarks, sources);
	}

	private static void appendHistoryGeographyContent(
			StringBuilder markdown, HistoryGeographySheet sheet, Map<String, SheetSource> sources) {
		if (sheet.keyConcepts().isEmpty() && sheet.chronology().isEmpty() && sheet.characters().isEmpty()
				&& sheet.definitions().isEmpty() && sheet.spatialLandmarks().isEmpty()) {
			return;
		}
		markdown.append("## Contenu issu du cours et du référentiel — avec sources\n\n");
		appendStatements(markdown, "### Notions clés", sheet.keyConcepts(), sources);
		appendChronology(markdown, sheet.chronology(), sources);
		appendCharacters(markdown, sheet.characters(), sources);
		appendDefinitions(markdown, "### Notions à définir", sheet.definitions(), sources);
		appendSpatialLandmarks(markdown, sheet.spatialLandmarks(), sources);
	}

	private static void appendMathContent(StringBuilder markdown, MathSheet sheet, Map<String, SheetSource> sources) {
		if (sheet.keyConcepts().isEmpty() && sheet.definitions().isEmpty() && sheet.properties().isEmpty()
				&& sheet.theorems().isEmpty() && sheet.formulas().isEmpty() && sheet.methods().isEmpty()
				&& sheet.commonErrors().isEmpty() && sheet.examples().isEmpty()) {
			return;
		}
		markdown.append("## Contenu issu du cours et du référentiel — avec sources\n\n");
		appendStatements(markdown, "### Notions clés", sheet.keyConcepts(), sources);
		appendDefinitions(markdown, sheet.definitions(), sources);
		appendMathProperties(markdown, sheet.properties(), sources);
		appendMathTheorems(markdown, sheet.theorems(), sources);
		appendMathFormulas(markdown, sheet.formulas(), sources);
		appendMathMethods(markdown, sheet.methods(), sources);
		appendMathErrors(markdown, sheet.commonErrors(), sources);
		appendMathExamples(markdown, sheet.examples(), sources);
	}

	private static void appendMathProperties(
			StringBuilder markdown, List<MathProperty> properties, Map<String, SheetSource> sources) {
		if (properties.isEmpty()) {
			return;
		}
		markdown.append("### Propriétés\n\n");
		for (MathProperty property : properties) {
			markdown.append("- **").append(requiredText(property.name())).append("** — ")
				.append(requiredText(property.statement())).append(" ")
				.append(citations(property.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendMathTheorems(
			StringBuilder markdown, List<MathTheorem> theorems, Map<String, SheetSource> sources) {
		if (theorems.isEmpty()) {
			return;
		}
		markdown.append("### Théorèmes\n\n");
		for (MathTheorem theorem : theorems) {
			markdown.append("- **").append(requiredText(theorem.name())).append("** — ")
				.append(requiredText(theorem.statement())).append(" ")
				.append(citations(theorem.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendMathFormulas(
			StringBuilder markdown, List<MathFormula> formulas, Map<String, SheetSource> sources) {
		if (formulas.isEmpty()) {
			return;
		}
		markdown.append("### Formules\n\n");
		for (MathFormula formula : formulas) {
			String latex = formula.latex();
			requiredText(latex);
			markdown.append("- **").append(requiredText(formula.name())).append("** : ")
				.append(latex);
			if (formula.requiresVerification()) {
				markdown.append(" [formule à vérifier]");
			}
			markdown.append(" — ").append(requiredText(formula.explanation())).append(" ")
				.append(citations(formula.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendMathMethods(
			StringBuilder markdown, List<MathMethod> methods, Map<String, SheetSource> sources) {
		if (methods.isEmpty()) {
			return;
		}
		markdown.append("### Méthodes\n\n");
		for (MathMethod method : methods) {
			markdown.append("**").append(requiredText(method.name())).append("** ")
				.append(citations(method.sourceIds(), sources)).append("\n\n");
			for (int index = 0; index < method.steps().size(); index++) {
				markdown.append(index + 1).append(". ").append(requiredText(method.steps().get(index))).append('\n');
			}
			markdown.append('\n');
		}
	}

	private static void appendMathErrors(
			StringBuilder markdown, List<MathCommonError> errors, Map<String, SheetSource> sources) {
		if (errors.isEmpty()) {
			return;
		}
		markdown.append("### Erreurs fréquentes\n\n");
		for (MathCommonError error : errors) {
			markdown.append("- **Erreur :** ").append(requiredText(error.error()))
				.append(" **Correction :** ").append(requiredText(error.correction())).append(" ")
				.append(citations(error.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendMathExamples(
			StringBuilder markdown, List<MathExample> examples, Map<String, SheetSource> sources) {
		if (examples.isEmpty()) {
			return;
		}
		markdown.append("### Exemples du cours\n\n");
		for (MathExample example : examples) {
			markdown.append("- ").append(requiredText(example.text())).append(" ")
				.append(citations(example.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendChronology(
			StringBuilder markdown, List<HistoryGeographyEvent> events, Map<String, SheetSource> sources) {
		if (events.isEmpty()) {
			return;
		}
		markdown.append("### Chronologie\n\n");
		for (HistoryGeographyEvent event : events) {
			markdown.append("- **").append(requiredText(event.date())).append("** — ")
				.append(requiredText(event.event())).append(" ")
				.append(citations(event.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendCharacters(
			StringBuilder markdown, List<HistoryGeographyCharacter> characters, Map<String, SheetSource> sources) {
		if (characters.isEmpty()) {
			return;
		}
		markdown.append("### Personnages\n\n");
		for (HistoryGeographyCharacter character : characters) {
			markdown.append("- **").append(requiredText(character.name())).append("** — ")
				.append(requiredText(character.role())).append(" ")
				.append(citations(character.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendSpatialLandmarks(
			StringBuilder markdown, List<HistoryGeographyLandmark> landmarks, Map<String, SheetSource> sources) {
		if (landmarks.isEmpty()) {
			return;
		}
		markdown.append("### Repères spatiaux\n\n");
		for (HistoryGeographyLandmark landmark : landmarks) {
			markdown.append("- ").append(requiredText(landmark.place())).append(" ")
				.append(citations(landmark.sourceIds(), sources)).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendGeneralKnowledge(StringBuilder markdown, List<SheetGeneralKnowledge> items) {
		if (items.isEmpty()) {
			return;
		}
		markdown.append("## Complément de connaissances générales — à vérifier\n\n");
		for (SheetGeneralKnowledge item : items) {
			if (item.origin() == null) {
				throw new IllegalStateException("Un élément du complément ne porte pas son origine.");
			}
			markdown.append("- **[").append(item.origin()).append("] — À vérifier** ")
				.append(requiredText(item.text())).append('\n');
		}
		markdown.append('\n');
	}

	private static void appendStatements(
			StringBuilder markdown,
			String heading,
			List<SheetStatement> statements,
			Map<String, SheetSource> sources) {
		if (statements.isEmpty()) {
			return;
		}
		markdown.append(heading).append("\n\n");
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
		appendDefinitions(markdown, "### Définitions", definitions, sources);
	}

	private static void appendDefinitions(
			StringBuilder markdown,
			String heading,
			List<SheetDefinition> definitions,
			Map<String, SheetSource> sources) {
		if (definitions.isEmpty()) {
			return;
		}
		markdown.append(heading).append("\n\n");
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

}
