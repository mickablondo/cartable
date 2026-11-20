package dev.mickablondo.cartable.course;

/**
 * Signale qu'un cours ou une page ne peut pas être relu dans son état courant.
 */
public class CourseReviewException extends RuntimeException {

	public CourseReviewException(String message) {
		super(message);
	}

	public CourseReviewException(String message, Throwable cause) {
		super(message, cause);
	}
}
