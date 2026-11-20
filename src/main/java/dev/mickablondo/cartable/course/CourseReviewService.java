package dev.mickablondo.cartable.course;

import java.util.List;
import java.util.UUID;

/**
 * Permet de relire et valider le texte transcrit des cours issus d'images.
 */
public interface CourseReviewService {

	/**
	 * Renvoie les pages d'un cours en attente de relecture.
	 * @param courseId identifiant du cours
	 * @return pages dans leur ordre d'origine
	 */
	List<CoursePageReview> findPages(UUID courseId);

	/**
	 * Enregistre le texte corrigé d'une page.
	 * @param courseId identifiant du cours
	 * @param pageNumber numéro de la page
	 * @param text texte corrigé
	 */
	void updatePageText(UUID courseId, int pageNumber, String text);

	/**
	 * Valide le texte relu et déclenche son indexation.
	 * @param courseId identifiant du cours
	 */
	void validate(UUID courseId);
}
