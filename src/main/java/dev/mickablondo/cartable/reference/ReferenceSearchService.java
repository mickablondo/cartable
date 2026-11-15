package dev.mickablondo.cartable.reference;

import java.util.List;

import dev.mickablondo.cartable.course.Subject;

/**
 * Recherche des passages pertinents dans le référentiel officiel indexé.
 */
public interface ReferenceSearchService {

	/**
	 * Recherche les passages proches de la requête, limités à la matière indiquée.
	 * Au plus quatre passages sont renvoyés.
	 * @param query requête de recherche
	 * @param subject matière choisie par l'utilisateur
	 * @return passages trouvés et leurs références
	 */
	List<ReferencePassage> search(String query, Subject subject);

}
