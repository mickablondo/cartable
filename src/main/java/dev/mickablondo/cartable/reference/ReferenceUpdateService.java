package dev.mickablondo.cartable.reference;

/**
 * Met à jour les documents officiels configurés et indexe leurs PDF.
 */
public interface ReferenceUpdateService {

	/**
	 * Télécharge ou utilise les PDF locaux correspondant au manifeste, puis indexe ceux qui sont valides.
	 * @return le résultat de la mise à jour, y compris les erreurs propres à chaque document
	 */
	ReferenceUpdateReport updateReference();

}
