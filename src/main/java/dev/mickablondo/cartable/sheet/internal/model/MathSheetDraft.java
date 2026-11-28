package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

/**
 * Fiche de mathématiques structurée retournée par le modèle.
 */
public record MathSheetDraft(
		String theme,
		MathCourseContent courseContent,
		List<SheetGeneralKnowledgeDraft> generalKnowledge,
		List<String> limitations) {
}
