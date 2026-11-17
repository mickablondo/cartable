package dev.mickablondo.cartable.course.internal.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import dev.mickablondo.cartable.course.CourseImageReadingRequested;
import dev.mickablondo.cartable.course.CourseStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class CourseImageReadingListener {

	private static final float PDF_RENDER_DPI = 150;

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate progressTransactions;
	private final VisionTextClient visionTextClient;
	private final Path dataDirectory;
	private final Resource transcriptionPrompt;

	CourseImageReadingListener(
			JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager,
			VisionTextClient visionTextClient,
			@Value("${cartable.course.data-directory:data}") Path dataDirectory,
			@Value("classpath:/prompts/image-transcription.st") Resource transcriptionPrompt) {
		this.jdbcTemplate = jdbcTemplate;
		this.progressTransactions = new TransactionTemplate(transactionManager);
		this.progressTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.visionTextClient = visionTextClient;
		this.dataDirectory = dataDirectory;
		this.transcriptionPrompt = transcriptionPrompt;
	}

	@ApplicationModuleListener
	void on(CourseImageReadingRequested event) {
		try {
			process(event.courseId());
		}
		catch (VisionTextException exception) {
			markFailed(event.courseId(), exception.getMessage());
		}
		catch (IOException exception) {
			markFailed(event.courseId(),
				"Impossible de lire une page du document. Vérifiez que le fichier est accessible et non endommagé.");
		}
	}

	private void process(UUID courseId) throws IOException {
		updateCourse(courseId, CourseStatus.TEXT_EXTRACTION_IN_PROGRESS, null, true, null);
		CourseSource course = findCourseSource(courseId);
		String prompt = readPrompt();
		List<PageSource> pages = findPages(courseId);
		if (!pages.isEmpty() && pages.getFirst().renderedFromPdf()) {
			transcribePdf(courseId, course, prompt);
		}
		else if (!pages.isEmpty()) {
			for (PageSource page : pages) {
				byte[] image = readImage(page);
				transcribePage(courseId, page, image, prompt);
			}
		}
		else if (course.sourcePath().toLowerCase().endsWith(".pdf")) {
			transcribePdf(courseId, course, prompt);
		}
		else {
			throw new VisionTextException("Aucune page image n'est disponible pour ce cours.");
		}
		completeCourse(courseId);
	}

	private void transcribePdf(UUID courseId, CourseSource course, String prompt) throws IOException {
		Path pdfPath = resolveSourcePath(course.sourcePath());
		try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
			int pageCount = document.getNumberOfPages();
			if (pageCount == 0) {
				throw new VisionTextException("Le document PDF ne contient aucune page lisible.");
			}
			List<PageSource> pages = findPages(courseId);
			if (pages.isEmpty()) {
				pages = new ArrayList<>(pageCount);
				for (int index = 0; index < pageCount; index++) {
					pages.add(new PageSource(index + 1, course.sourcePath(), "image/png", true));
				}
				storePdfPages(courseId, course.fileName(), pages);
			}

			PDFRenderer renderer = new PDFRenderer(document);
			for (int index = 0; index < pages.size(); index++) {
				BufferedImage renderedPage = renderer.renderImageWithDPI(index, PDF_RENDER_DPI);
				try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
					if (!ImageIO.write(renderedPage, "png", output)) {
						throw new VisionTextException("Impossible de convertir une page PDF en image.");
					}
					transcribePage(courseId, pages.get(index), output.toByteArray(), prompt);
				}
				finally {
					renderedPage.flush();
				}
			}
		}
	}

	private void transcribePage(UUID courseId, PageSource page, byte[] image, String prompt) {
		try {
			String text = visionTextClient.transcribe(prompt, image, page.mediaType());
			progressTransactions.executeWithoutResult(status -> jdbcTemplate.update("""
				UPDATE course_page
				SET extracted_text = ?
				WHERE course_id = ? AND page_number = ?
				""", text, courseId, page.pageNumber()));
		}
		catch (VisionTextException exception) {
			throw new VisionTextException(
				"Échec de lecture de la page %d : %s".formatted(page.pageNumber(), exception.getMessage()), exception);
		}
	}

	private byte[] readImage(PageSource page) throws IOException {
		return Files.readAllBytes(resolveSourcePath(page.sourcePath()));
	}

	private String readPrompt() throws IOException {
		try (InputStream input = transcriptionPrompt.getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private CourseSource findCourseSource(UUID courseId) {
		return jdbcTemplate.queryForObject("""
			SELECT file_name, source_path FROM course WHERE id = ?
			""",
			(resultSet, rowNumber) ->
				new CourseSource(resultSet.getString("file_name"), resultSet.getString("source_path")),
			courseId);
	}

	private List<PageSource> findPages(UUID courseId) {
		return jdbcTemplate.query("""
			SELECT page_number, source_path, media_type, rendered_from_pdf
			FROM course_page WHERE course_id = ? ORDER BY page_number
			""",
			(resultSet, rowNumber) -> new PageSource(
				resultSet.getInt("page_number"),
				resultSet.getString("source_path"),
				resultSet.getString("media_type"),
				resultSet.getBoolean("rendered_from_pdf")),
			courseId);
	}

	private void storePdfPages(UUID courseId, String fileName, List<PageSource> pages) {
		progressTransactions.executeWithoutResult(status -> {
			for (PageSource page : pages) {
				jdbcTemplate.update("""
					INSERT INTO course_page (
						course_id, page_number, file_name, source_path, media_type, rendered_from_pdf
					)
					VALUES (?, ?, ?, ?, ?, TRUE)
					""",
					courseId,
					page.pageNumber(),
					"%s — page %d".formatted(fileName, page.pageNumber()),
					page.sourcePath(),
					page.mediaType());
			}
		});
	}

	private void completeCourse(UUID courseId) {
		progressTransactions.executeWithoutResult(status -> {
			String extractedText = jdbcTemplate.query("""
				SELECT page_number, extracted_text FROM course_page
				WHERE course_id = ? ORDER BY page_number
				""",
				resultSet -> {
					StringBuilder text = new StringBuilder();
					while (resultSet.next()) {
						text.append("--- Page ")
							.append(resultSet.getInt("page_number"))
							.append(" ---\n")
							.append(resultSet.getString("extracted_text"))
							.append("\n\n");
					}
					return text.toString().stripTrailing();
				},
				courseId);
			jdbcTemplate.update("""
				UPDATE course
				SET extracted_text = ?, status = ?, requires_image_reading = FALSE, failure_message = NULL
				WHERE id = ?
				""",
				extractedText,
				CourseStatus.TEXT_TO_REVIEW.name(),
				courseId);
		});
	}

	private void updateCourse(
			UUID courseId, CourseStatus status, String extractedText, boolean requiresImageReading, String failureMessage) {
		progressTransactions.executeWithoutResult(transactionStatus -> jdbcTemplate.update("""
			UPDATE course
			SET status = ?, extracted_text = COALESCE(?, extracted_text),
				requires_image_reading = ?, failure_message = ?
			WHERE id = ?
			""",
			status.name(), extractedText, requiresImageReading, failureMessage, courseId));
	}

	private void markFailed(UUID courseId, String message) {
		updateCourse(courseId, CourseStatus.FAILED, null, false, message);
	}

	private Path resolveSourcePath(String sourcePath) {
		Path root = dataDirectory.toAbsolutePath().normalize();
		Path resolved = root.resolve(sourcePath).normalize();
		if (!resolved.startsWith(root)) {
			throw new VisionTextException("Le chemin du fichier de cours n'est pas valide.");
		}
		return resolved;
	}

	private record CourseSource(String fileName, String sourcePath) {
	}

	private record PageSource(int pageNumber, String sourcePath, String mediaType, boolean renderedFromPdf) {
	}

}
