package dev.mickablondo.cartable.reference;

/**
 * Résultat d'un import du référentiel.
 * @param importedDocuments nombre de documents indexés ou mis à jour
 * @param skippedDocuments nombre de documents déjà à jour
 * @param indexedChunks nombre total de passages indexés
 */
public record ReferenceImportResult(int importedDocuments, int skippedDocuments, int indexedChunks) {
}
