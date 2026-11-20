package dev.mickablondo.cartable.course.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.mickablondo.cartable.course.CourseImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

class CourseImportServiceImplTests {

	@Test
	void rejectsImageBeyondTheConfiguredPerCourseCount(@TempDir Path directory) throws Exception {
		Path first = write(directory.resolve("first.png"), 1);
		Path second = write(directory.resolve("second.jpg"), 1);
		CourseImportServiceImpl service = service(directory, limits(1, 3, 3, 2));

		CourseImportException exception = assertThrows(CourseImportException.class,
			() -> service.importCourse(java.util.List.of(first, second), dev.mickablondo.cartable.course.Subject.FRENCH));

		assertThat(exception.getReason()).isEqualTo(CourseImportException.Reason.TOO_MANY_IMAGES);
		assertThat(exception.getMessage()).contains("plus de 1 images");
	}

	@Test
	void rejectsImagesLargerThanTheirConfiguredLimitBeforeReadingThem(@TempDir Path directory) throws Exception {
		Path image = write(directory.resolve("large.png"), 4);
		CourseImportServiceImpl service = service(directory, limits(1, 3, 3, 2));

		CourseImportException exception = assertThrows(CourseImportException.class,
			() -> service.importCourse(image, dev.mickablondo.cartable.course.Subject.FRENCH));

		assertThat(exception.getReason()).isEqualTo(CourseImportException.Reason.FILE_TOO_LARGE);
		assertThat(exception.getMessage()).contains("dépasse la limite configurée");
	}

	@Test
	void rejectsScannedPdfsLargerThanTheirConfiguredLimit(@TempDir Path directory) throws Exception {
		Path pdf = write(directory.resolve("large.pdf"), 4);
		CourseImportServiceImpl service = service(directory, limits(1, 3, 3, 2));

		CourseImportException exception = assertThrows(CourseImportException.class,
			() -> service.importCourse(pdf, dev.mickablondo.cartable.course.Subject.FRENCH));

		assertThat(exception.getReason()).isEqualTo(CourseImportException.Reason.FILE_TOO_LARGE);
	}

	@Test
	void rejectsRequestLimitThatCannotFitMultipleMaximumSizeImages() {
		assertThrows(IllegalArgumentException.class, () -> new CourseUploadProperties(
			20,
			DataSize.ofMegabytes(15),
			DataSize.ofMegabytes(25),
			DataSize.ofMegabytes(25),
			DataSize.ofMegabytes(45)));
	}

	@Test
	void rejectsRequestLimitSmallerThanTheMaximumSingleFile() {
		assertThrows(IllegalArgumentException.class, () -> new CourseUploadProperties(
			20,
			DataSize.ofMegabytes(15),
			DataSize.ofMegabytes(25),
			DataSize.ofMegabytes(60),
			DataSize.ofMegabytes(55)));
	}

	private static CourseImportServiceImpl service(Path dataDirectory, CourseUploadProperties limits) {
		return new CourseImportServiceImpl(
			mock(JdbcTemplate.class),
			mock(ApplicationEventPublisher.class),
			mock(TransactionTemplate.class),
			dataDirectory,
			limits);
	}

	private static CourseUploadProperties limits(
			int maxImages, long maxImageBytes, long maxPdfBytes, int requestSizeMegabytes) {
		long maxFileBytes = Math.max(maxImageBytes, maxPdfBytes);
		return new CourseUploadProperties(
			maxImages,
			DataSize.ofBytes(maxImageBytes),
			DataSize.ofBytes(maxPdfBytes),
			DataSize.ofBytes(maxFileBytes),
			DataSize.ofMegabytes(requestSizeMegabytes));
	}

	private static Path write(Path path, int size) throws Exception {
		Files.write(path, new byte[size]);
		return path;
	}
}
