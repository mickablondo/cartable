package dev.mickablondo.cartable.sheet.internal.configuration;

/**
 * Métadonnées de sélection d'une variante pour une matière donnée.
 */
public record SheetVariantDefinition(String sheetType, String label, int schemaVersion, String promptResource) {
}
