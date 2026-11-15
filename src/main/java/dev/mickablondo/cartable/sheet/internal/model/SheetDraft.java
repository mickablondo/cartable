package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetStatement;

/**
 * Contenu structuré retourné par le modèle, avant validation des citations.
 */
public record SheetDraft(
		String theme,
		List<SheetStatement> keyConcepts,
		List<SheetDefinition> definitions,
		List<SheetStatement> landmarks,
		List<String> limitations) {
}
