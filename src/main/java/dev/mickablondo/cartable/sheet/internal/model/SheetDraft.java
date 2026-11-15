package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

import dev.mickablondo.cartable.sheet.SheetCourseContent;

/**
 * Contenu structuré retourné par le modèle, avant validation des citations.
 */
public record SheetDraft(
		String theme,
		SheetCourseContent courseContent,
		List<SheetGeneralKnowledgeDraft> generalKnowledge,
		List<String> limitations) {
}
