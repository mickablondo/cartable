package dev.mickablondo.cartable.reference;

import java.time.LocalDate;

/**
 * Passage retrouvé dans le référentiel officiel avec sa référence.
 * @param text texte du passage
 * @param title titre du document officiel
 * @param sourceUrl URL de la source officielle
 * @param sourceFile nom du fichier indexé
 * @param chapter chapitre du document
 * @param publicationDate date de publication du document
 */
public record ReferencePassage(
		String text,
		String title,
		String sourceUrl,
		String sourceFile,
		String chapter,
		LocalDate publicationDate) {
}
