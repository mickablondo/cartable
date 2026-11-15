package dev.mickablondo.cartable.course;

import java.util.UUID;

/**
 * Représente l'importation d'un cours.
 * @param courseId identifiant unique du cours importé
 */
public record CourseImported(UUID courseId) {
}
