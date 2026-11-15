package dev.mickablondo.cartable.course;

import java.time.Instant;
import java.util.UUID;

/**
 * Informations affichables sur un cours importé.
 * @param id identifiant du cours
 * @param fileName nom du fichier d'origine
 * @param subject matière choisie à l'import
 * @param importedAt date d'import
 * @param status état courant du cours
 */
public record CourseSummary(UUID id, String fileName, Subject subject, Instant importedAt, String status) {
}
