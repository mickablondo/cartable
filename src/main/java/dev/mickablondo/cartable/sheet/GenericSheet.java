package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;

/**
 * Fiche générique, utilisée par défaut tant qu'aucune variante spécialisée n'existe.
 */
public record GenericSheet(
		UUID id,
		Subject subject,
		String theme,
		String sheetType,
		int schemaVersion,
		List<SheetStatement> keyConcepts,
		List<SheetDefinition> definitions,
		List<SheetStatement> landmarks,
		List<SheetGeneralKnowledge> generalKnowledge,
		List<String> limitations,
		List<SheetSource> sources,
		SheetStatus status,
		Instant generatedAt) implements Sheet {

	public GenericSheet {
		keyConcepts = List.copyOf(keyConcepts);
		definitions = List.copyOf(definitions);
		landmarks = List.copyOf(landmarks);
		generalKnowledge = List.copyOf(generalKnowledge);
		limitations = List.copyOf(limitations);
		sources = List.copyOf(sources);
	}

}
