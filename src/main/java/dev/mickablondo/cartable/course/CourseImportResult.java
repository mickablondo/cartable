package dev.mickablondo.cartable.course;

import java.time.Instant;
import java.util.UUID;

/**
 * Représente l'importation d'un cours.
 * @param id identifiant unique de l'importation du cours
 * @param fileName nom du fichier importé
 * @param subject matière du cours
 * @param importedAt date d'importation
 * @param status statut de l'importation
 */
public record CourseImportResult(UUID id, String fileName, Subject subject, Instant importedAt, String status) {
}
