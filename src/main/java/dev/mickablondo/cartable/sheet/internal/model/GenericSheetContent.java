package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetStatement;

/**
 * Données propres à la variante générique, stockées dans variant_data.
 */
public record GenericSheetContent(List<SheetDefinition> definitions, List<SheetStatement> landmarks) {
}
