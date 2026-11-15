package dev.mickablondo.cartable.reference;

/**
 * Signale une erreur lisible lors de l'import d'un document du référentiel.
 */
public class ReferenceImportException extends RuntimeException {

	public ReferenceImportException(String message) {
		super(message);
	}

	public ReferenceImportException(String message, Throwable cause) {
		super(message, cause);
	}

}
