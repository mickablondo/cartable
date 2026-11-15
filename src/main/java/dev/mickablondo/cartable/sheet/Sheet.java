package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;

/**
 * Fiche générique produite à partir de passages retrouvés.
 * @param id identifiant unique de la fiche
 * @param subject matière choisie par l'utilisateur
 * @param theme thème de la fiche
 * @param keyConcepts notions clés avec leurs sources
 * @param definitions définitions avec leurs sources
 * @param landmarks repères avec leurs sources
 * @param limitations éléments que les passages ne permettent pas de préciser
 * @param sources références des passages cités
 * @param status statut de validation
 * @param generatedAt date de génération
 */
public record Sheet(
		UUID id,
		Subject subject,
		String theme,
		List<SheetStatement> keyConcepts,
		List<SheetDefinition> definitions,
		List<SheetStatement> landmarks,
		List<String> limitations,
		List<SheetSource> sources,
		SheetStatus status,
		Instant generatedAt) {

	public Sheet(
			UUID id,
			Subject subject,
			String theme,
			List<SheetStatement> keyConcepts,
			List<SheetDefinition> definitions,
			List<SheetStatement> landmarks,
			List<String> limitations,
			List<SheetSource> sources,
			Instant generatedAt) {
		this(id, subject, theme, keyConcepts, definitions, landmarks, limitations, sources, SheetStatus.TO_VERIFY,
			generatedAt);
	}

}
