package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

/**
 * Contenu structuré de la fiche d'histoire-géographie retourné par le modèle.
 */
public record HistoryGeographySheetDraft(
		String theme,
		HistoryGeographyCourseContent courseContent,
		List<SheetGeneralKnowledgeDraft> generalKnowledge,
		List<String> limitations) {
}
