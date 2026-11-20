package dev.mickablondo.cartable.course;

/**
 * Gestion des exceptions liées à l'import de cours.
 */
public class CourseImportException extends RuntimeException {

	private final Reason reason;

	public CourseImportException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public CourseImportException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

	public enum Reason {
		NO_FILES,
		EMPTY_FILE,
		UNSUPPORTED_FORMAT,
		NO_TEXT_EXTRACTED,
		FILE_NOT_READABLE,
		FILE_TOO_LARGE,
		TEXT_EXTRACTION_FAILED,
		FILE_STORAGE_FAILED,
		TOO_MANY_IMAGES,
		COURSE_NOT_FOUND
	}
}
