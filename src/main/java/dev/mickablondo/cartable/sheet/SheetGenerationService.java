package dev.mickablondo.cartable.sheet;

import dev.mickablondo.cartable.course.Subject;

/**
 * Génère et enregistre une fiche de révision à partir des passages retrouvés.
 */
public interface SheetGenerationService {

	/**
	 * Génère une fiche générique pour le thème et la matière demandés.
	 * @param theme thème de révision demandé
	 * @param subject matière choisie par l'utilisateur
	 * @return fiche créée au statut TO_VERIFY
	 */
	Sheet generate(String theme, Subject subject);

}
