package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

/**
 * Élément de complément retourné par le modèle, avant contrôle des citations.
 */
public record SheetGeneralKnowledgeDraft(String text, List<String> sourceIds) {
}
