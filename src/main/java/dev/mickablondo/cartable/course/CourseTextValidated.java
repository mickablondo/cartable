package dev.mickablondo.cartable.course;

import java.util.UUID;

/**
 * Indique qu'un texte issu d'images a été relu et peut être indexé.
 * @param courseId identifiant du cours validé
 */
public record CourseTextValidated(UUID courseId) {
}
