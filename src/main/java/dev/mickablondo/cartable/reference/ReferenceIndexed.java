package dev.mickablondo.cartable.reference;

import java.util.UUID;

/**
 * Signale l'indexation d'un document du référentiel.
 * @param referenceId identifiant stable du document
 * @param title titre du document officiel
 * @param sourceUrl URL de la source officielle
 * @param indexedChunks nombre de passages indexés
 */
public record ReferenceIndexed(UUID referenceId, String title, String sourceUrl, int indexedChunks) {
}
