package dev.mickablondo.cartable.sheet;

import java.time.LocalDate;

/**
 * Référence d'un passage utilisé dans une fiche.
 * @param id identifiant attribué au passage pour ses citations
 * @param origin origine du passage
 * @param sourceFile nom du fichier source
 * @param chapter chapitre du passage
 * @param title titre du document officiel
 * @param sourceUrl URL du document officiel
 * @param publicationDate date de publication du document officiel
 */
public record SheetSource(
		String id,
		String origin,
		String sourceFile,
		String chapter,
		String title,
		String sourceUrl,
		LocalDate publicationDate) {
}
