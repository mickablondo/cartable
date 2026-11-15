package dev.mickablondo.cartable.sheet;

import java.util.UUID;

/**
 * Valide explicitement une fiche de révision.
 */
public interface SheetVerificationService {

	/**
	 * Fait passer une fiche de TO_VERIFY à VERIFIED et publie SheetVerified.
	 * @param sheetId identifiant de la fiche à valider
	 * @throws IllegalArgumentException si la fiche n'existe pas
	 * @throws IllegalStateException si la fiche a déjà été validée
	 */
	void verify(UUID sheetId);

}
