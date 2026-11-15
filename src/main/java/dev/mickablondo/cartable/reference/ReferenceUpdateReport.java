package dev.mickablondo.cartable.reference;

import java.util.List;

/**
 * Résultat agrégé d'une mise à jour du référentiel.
 */
public record ReferenceUpdateReport(
		int updatedDocuments,
		int unchangedDocuments,
		int indexedChunks,
		List<ReferenceUpdateIssue> issues) {

	public ReferenceUpdateReport {
		issues = List.copyOf(issues);
	}

}
