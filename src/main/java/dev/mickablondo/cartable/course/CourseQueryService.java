package dev.mickablondo.cartable.course;

import java.util.List;

/**
 * Consulte les informations des cours importés.
 */
public interface CourseQueryService {

	/**
	 * Renvoie les cours importés, du plus récent au plus ancien.
	 * @return cours importés avec leur matière et leur état d'indexation
	 */
	List<CourseSummary> findAll();

}
