package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;

/**
 * Fiche spécialisée de mathématiques.
 */
public record MathSheet(
		UUID id,
		Subject subject,
		String theme,
		String sheetType,
		int schemaVersion,
		List<SheetStatement> keyConcepts,
		List<SheetDefinition> definitions,
		List<MathProperty> properties,
		List<MathTheorem> theorems,
		List<MathFormula> formulas,
		List<MathMethod> methods,
		List<MathCommonError> commonErrors,
		List<MathExample> examples,
		List<SheetGeneralKnowledge> generalKnowledge,
		List<String> limitations,
		List<SheetSource> sources,
		SheetStatus status,
		Instant generatedAt) implements Sheet {

	public MathSheet {
		keyConcepts = List.copyOf(keyConcepts);
		definitions = List.copyOf(definitions);
		properties = List.copyOf(properties);
		theorems = List.copyOf(theorems);
		formulas = List.copyOf(formulas);
		methods = List.copyOf(methods);
		commonErrors = List.copyOf(commonErrors);
		examples = List.copyOf(examples);
		generalKnowledge = List.copyOf(generalKnowledge);
		limitations = List.copyOf(limitations);
		sources = List.copyOf(sources);
	}

}
