package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;

/**
 * Événement publié après la validation explicite d'une fiche.
 * @param sheetId identifiant de la fiche validée
 * @param subject matière de la fiche
 * @param theme thème de la fiche
 * @param verifiedAt date de validation
 */
public record SheetVerified(UUID sheetId, Subject subject, String theme, Instant verifiedAt) {
}
