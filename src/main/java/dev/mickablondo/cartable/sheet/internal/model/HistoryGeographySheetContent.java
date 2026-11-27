package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

import dev.mickablondo.cartable.sheet.HistoryGeographyCharacter;
import dev.mickablondo.cartable.sheet.HistoryGeographyEvent;
import dev.mickablondo.cartable.sheet.HistoryGeographyLandmark;
import dev.mickablondo.cartable.sheet.SheetDefinition;

/**
 * Contenu propre à la variante, stocké dans variant_data.
 */
public record HistoryGeographySheetContent(
		List<HistoryGeographyEvent> chronology,
		List<HistoryGeographyCharacter> characters,
		List<SheetDefinition> definitions,
		List<HistoryGeographyLandmark> spatialLandmarks) {
}
