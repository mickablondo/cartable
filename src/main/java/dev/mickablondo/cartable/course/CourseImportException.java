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
		EMPTY_FILE,
		UNSUPPORTED_FORMAT,
		SCAN_NOT_SUPPORTED,
		NO_TEXT_EXTRACTED,
		FILE_NOT_READABLE,
		TEXT_EXTRACTION_FAILED,
		FILE_STORAGE_FAILED,
		COURSE_NOT_FOUND
	}
}
