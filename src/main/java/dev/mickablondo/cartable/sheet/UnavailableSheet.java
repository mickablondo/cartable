package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;

/**
 * Représentation dégradée d'une fiche dont le type ou le contenu n'est pas lisible.
 */
public record UnavailableSheet(
		UUID id,
		Subject subject,
		String theme,
		String sheetType,
		int schemaVersion,
		List<SheetStatement> keyConcepts,
		List<SheetGeneralKnowledge> generalKnowledge,
		List<String> limitations,
		List<SheetSource> sources,
		SheetStatus status,
		Instant generatedAt,
		String degradationMessage) implements Sheet {

	public UnavailableSheet {
		keyConcepts = List.copyOf(keyConcepts);
		generalKnowledge = List.copyOf(generalKnowledge);
		limitations = List.copyOf(limitations);
		sources = List.copyOf(sources);
	}

}
