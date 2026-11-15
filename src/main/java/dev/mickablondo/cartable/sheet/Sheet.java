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
 * @param courseContent contenu étayé par des sources
 * @param generalKnowledge complément sans source, à vérifier
 * @param limitations éléments que les passages ne permettent pas de préciser
 * @param sources références des passages cités
 * @param status statut de validation
 * @param generatedAt date de génération
 */
public record Sheet(
		UUID id,
		Subject subject,
		String theme,
		SheetCourseContent courseContent,
		List<SheetGeneralKnowledge> generalKnowledge,
		List<String> limitations,
		List<SheetSource> sources,
		SheetStatus status,
		Instant generatedAt) {

	public Sheet(
			UUID id,
			Subject subject,
			String theme,
			SheetCourseContent courseContent,
			List<SheetGeneralKnowledge> generalKnowledge,
			List<String> limitations,
			List<SheetSource> sources,
			Instant generatedAt) {
		this(id, subject, theme, courseContent, generalKnowledge, limitations, sources, SheetStatus.TO_VERIFY,
			generatedAt);
	}

}
