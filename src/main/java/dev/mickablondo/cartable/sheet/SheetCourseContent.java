package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Contenu de la fiche étayé par des passages de cours ou du référentiel.
 */
public record SheetCourseContent(
		List<SheetStatement> keyConcepts,
		List<SheetDefinition> definitions,
		List<SheetStatement> landmarks) {
}
