package dev.mickablondo.cartable.course;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Interface représentant un service d'import de cours.
 * Il fournit des méthodes pour importer un cours à partir d'un fichier et pour obtenir le texte extrait d'un cours importé.
 */
public interface CourseImportService {

	/**
	 * Importe un cours à partir d'un fichier donné et d'une matière spécifiée.
	 * @param file le fichier contenant le cours à importer
	 * @param subject la matière du cours
	 * @return un objet CourseImportResult représentant le résultat de l'importation
	 */
	CourseImportResult importCourse(Path file, Subject subject);

	/**
	 * Récupère le texte extrait d'un cours importé à partir de son identifiant unique.
	 * @param courseId l'identifiant unique du cours importé
	 * @return le texte extrait du cours
	 */
	String getExtractedText(UUID courseId);

}
