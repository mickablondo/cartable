package dev.mickablondo.cartable.course;

import java.util.UUID;

/**
 * Représente l'indexation des passages d'un cours.
 * @param courseId identifiant du cours indexé
 * @param indexedChunks nombre de passages indexés
 */
public record CourseIndexed(UUID courseId, int indexedChunks) {
}
