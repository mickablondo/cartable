package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

import dev.mickablondo.cartable.sheet.MathCommonError;
import dev.mickablondo.cartable.sheet.MathExample;
import dev.mickablondo.cartable.sheet.MathFormula;
import dev.mickablondo.cartable.sheet.MathMethod;
import dev.mickablondo.cartable.sheet.MathProperty;
import dev.mickablondo.cartable.sheet.MathTheorem;
import dev.mickablondo.cartable.sheet.SheetDefinition;

/**
 * Contenu spécialisé de mathématiques stocké dans variant_data.
 */
public record MathSheetContent(
		List<SheetDefinition> definitions,
		List<MathProperty> properties,
		List<MathTheorem> theorems,
		List<MathFormula> formulas,
		List<MathMethod> methods,
		List<MathCommonError> commonErrors,
		List<MathExample> examples) {
}
