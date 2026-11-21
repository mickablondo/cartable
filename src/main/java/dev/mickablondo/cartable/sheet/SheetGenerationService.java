package dev.mickablondo.cartable.sheet;

import java.util.List;

import dev.mickablondo.cartable.course.Subject;

/**
 * Génère et enregistre une fiche de révision à partir des passages retrouvés.
 */
public interface SheetGenerationService {

	/**
	 * Génère la fiche par défaut pour le thème et la matière demandés.
	 * @param theme thème de révision demandé
	 * @param subject matière choisie par l'utilisateur
	 * @return fiche créée au statut TO_VERIFY
	 */
	Sheet generate(String theme, Subject subject);

	/**
	 * Génère la variante explicitement choisie par l'utilisateur.
	 * @param theme thème de révision demandé
	 * @param subject matière choisie par l'utilisateur
	 * @param sheetType type de fiche, ou {@code null} pour le type par défaut
	 * @return fiche créée au statut TO_VERIFY
	 */
	Sheet generate(String theme, Subject subject, String sheetType);

	/**
	 * Types de fiche actuellement proposés dans le formulaire.
	 * @return types disponibles
	 */
	List<SheetTypeOption> availableTypes();

}
