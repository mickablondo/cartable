package dev.mickablondo.cartable.course;

import java.util.UUID;

/**
 * Demande la lecture des pages d'un cours par le modèle de vision.
 * @param courseId identifiant du cours à traiter
 */
public record CourseImageReadingRequested(UUID courseId) {
}
