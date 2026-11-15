package dev.mickablondo.cartable.sheet;

import java.util.UUID;

/**
 * Exporte une fiche et ses références en Markdown.
 */
public interface SheetMarkdownExportService {

	/**
	 * Produit l'export Markdown d'une fiche.
	 * Une fiche non validée porte un avertissement visible dans l'export.
	 * @param sheetId identifiant de la fiche à exporter
	 * @return contenu Markdown
	 * @throws IllegalArgumentException si la fiche n'existe pas
	 */
	String exportMarkdown(UUID sheetId);

}
