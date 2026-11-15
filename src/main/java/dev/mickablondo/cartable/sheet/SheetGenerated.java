package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;

/**
 * Événement publié après l'enregistrement d'une fiche.
 * @param sheetId identifiant de la fiche enregistrée
 * @param subject matière de la fiche
 * @param theme thème de la fiche
 * @param generatedAt date de génération de la fiche
 */
public record SheetGenerated(UUID sheetId, Subject subject, String theme, Instant generatedAt) {
}
