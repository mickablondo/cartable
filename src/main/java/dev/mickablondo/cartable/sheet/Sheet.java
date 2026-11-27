package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;

/**
 * Partie commune aux fiches, indépendamment du contenu propre à leur variante.
 */
public sealed interface Sheet permits GenericSheet, HistoryGeographySheet, UnavailableSheet {

	UUID id();

	Subject subject();

	String theme();

	String sheetType();

	int schemaVersion();

	List<SheetStatement> keyConcepts();

	List<SheetGeneralKnowledge> generalKnowledge();

	List<String> limitations();

	List<SheetSource> sources();

	SheetStatus status();

	Instant generatedAt();

	default String degradationMessage() {
		return null;
	}

}
