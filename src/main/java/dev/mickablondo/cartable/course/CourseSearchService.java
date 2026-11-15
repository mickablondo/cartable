package dev.mickablondo.cartable.course;

import java.util.List;

/**
 * Recherche des passages pertinents dans les cours indexés.
 */
public interface CourseSearchService {

	/**
	 * Recherche les passages proches de la requête, limités à la matière indiquée.
	 * Au plus quatre passages sont renvoyés.
	 * @param query requête de recherche
	 * @param subject matière choisie par l'utilisateur
	 * @return passages trouvés et leurs références
	 */
	List<CoursePassage> search(String query, Subject subject);

}
