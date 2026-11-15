package dev.mickablondo.cartable.sheet;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulte les fiches enregistrées.
 */
public interface SheetQueryService {

	/**
	 * Recherche une fiche par son identifiant.
	 * @param sheetId identifiant de la fiche
	 * @return la fiche si elle existe
	 */
	Optional<Sheet> findById(UUID sheetId);

}
