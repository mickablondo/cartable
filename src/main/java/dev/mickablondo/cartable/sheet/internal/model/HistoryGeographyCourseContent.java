package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

import dev.mickablondo.cartable.sheet.HistoryGeographyCharacter;
import dev.mickablondo.cartable.sheet.HistoryGeographyEvent;
import dev.mickablondo.cartable.sheet.HistoryGeographyLandmark;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetStatement;

/**
 * Contenu d'histoire-géographie étayé par des passages.
 */
public record HistoryGeographyCourseContent(
		List<SheetStatement> keyConcepts,
		List<HistoryGeographyEvent> chronology,
		List<HistoryGeographyCharacter> characters,
		List<SheetDefinition> definitions,
		List<HistoryGeographyLandmark> spatialLandmarks) {
}
