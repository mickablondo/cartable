package dev.mickablondo.cartable.reference;

/**
 * Importe et indexe les documents officiels déposés dans le dossier du référentiel.
 */
public interface ReferenceImportService {

	/**
	 * Importe les PDF accompagnés de leur description YAML.
	 * @return le nombre de documents indexés et ignorés
	 */
	ReferenceImportResult importReferences();

}
