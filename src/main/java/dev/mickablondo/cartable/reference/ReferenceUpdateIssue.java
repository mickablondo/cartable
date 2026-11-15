package dev.mickablondo.cartable.reference;

/**
 * Échec de téléchargement ou d'indexation d'un document du référentiel.
 */
public record ReferenceUpdateIssue(String documentId, String pdfUrl, String reason, boolean localPdfUsed) {
}
