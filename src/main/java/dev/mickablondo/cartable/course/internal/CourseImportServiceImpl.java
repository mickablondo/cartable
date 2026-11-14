package dev.mickablondo.cartable.course.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mickablondo.cartable.course.CourseImportException;
import dev.mickablondo.cartable.course.CourseImportResult;
import dev.mickablondo.cartable.course.CourseImportService;
import dev.mickablondo.cartable.course.CourseImported;
import dev.mickablondo.cartable.course.Subject;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class CourseImportServiceImpl implements CourseImportService {

	private static final String IMPORTED_STATUS = "IMPORTED";

	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactionTemplate;
	private final Path dataDirectory;

	CourseImportServiceImpl(
			JdbcTemplate jdbcTemplate,
			ApplicationEventPublisher events,
			TransactionTemplate transactionTemplate,
			@Value("${cartable.course.data-directory:data}") Path dataDirectory) {
		this.jdbcTemplate = jdbcTemplate;
		this.events = events;
		this.transactionTemplate = transactionTemplate;
		this.dataDirectory = dataDirectory;
	}

	@Override
	public CourseImportResult importCourse(Path file, Subject subject) {
		if (subject == null) {
			throw new IllegalArgumentException("La matière est obligatoire.");
		}
		if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
			throw new CourseImportException(CourseImportException.Reason.FILE_NOT_READABLE,
				"Le fichier du cours est inaccessible.");
		}

		String fileName = file.getFileName().toString();
		byte[] fileContent;
		try {
			fileContent = Files.readAllBytes(file);
		}
		catch (IOException exception) {
			throw new CourseImportException(CourseImportException.Reason.FILE_NOT_READABLE,
				"Impossible de lire le fichier du cours.", exception);
		}
		if (fileContent.length == 0) {
			throw new CourseImportException(CourseImportException.Reason.EMPTY_FILE,
				"Le fichier du cours est vide.");
		}

		String extension = supportedExtension(fileName);
		String extractedText = extractText(fileContent, fileName);
		if (extractedText.isBlank()) {
			if (extension.equals(".pdf")) {
				throw new CourseImportException(CourseImportException.Reason.SCAN_NOT_SUPPORTED,
					"PDF sans texte détectable : scan non supporté avant la V2.");
			}
			throw new CourseImportException(CourseImportException.Reason.NO_TEXT_EXTRACTED,
				"Aucun texte n'a été extrait du document Word.");
		}

		UUID courseId = UUID.randomUUID();
		Instant importedAt = Instant.now();
		String relativeFilePath = "courses/" + courseId + extension;
		Path storedFile = dataDirectory.resolve(relativeFilePath).normalize();
		storeFile(storedFile, fileContent);

		CourseImportResult result = new CourseImportResult(courseId, fileName, subject, importedAt, IMPORTED_STATUS);
		try {
			return transactionTemplate.execute(status -> {
				jdbcTemplate.update("""
					INSERT INTO course (id, file_name, subject, imported_at, status, source_path, extracted_text)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""",
					courseId,
					fileName,
					subject.name(),
					Timestamp.from(importedAt),
					IMPORTED_STATUS,
					relativeFilePath,
					extractedText);
				events.publishEvent(new CourseImported(courseId));
				return result;
			});
		}
		catch (RuntimeException exception) {
			try {
				Files.deleteIfExists(storedFile);
			}
			catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			throw exception;
		}
	}

	@Override
	public String getExtractedText(UUID courseId) {
		try {
			return jdbcTemplate.queryForObject(
				"SELECT extracted_text FROM course WHERE id = ?", String.class, courseId);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new CourseImportException(CourseImportException.Reason.COURSE_NOT_FOUND,
				"Aucun cours ne correspond à cet identifiant.", exception);
		}
	}

	private static String supportedExtension(String fileName) {
		int extensionSeparator = fileName.lastIndexOf('.');
		String extension = extensionSeparator < 0 ? "" : fileName.substring(extensionSeparator).toLowerCase(Locale.ROOT);
		if (!extension.equals(".pdf") && !extension.equals(".doc") && !extension.equals(".docx")) {
			throw new CourseImportException(CourseImportException.Reason.UNSUPPORTED_FORMAT,
				"Format non supporté : seuls les fichiers PDF et Word (.doc, .docx) sont acceptés.");
		}
		return extension;
	}

	private static String extractText(byte[] fileContent, String fileName) {
		try {
			return new TikaDocumentReader(new ByteArrayResource(fileContent, fileName)).get().stream()
				.map(document -> document.getText())
				.collect(Collectors.joining(System.lineSeparator()))
				.strip();
		}
		catch (RuntimeException exception) {
			throw new CourseImportException(CourseImportException.Reason.TEXT_EXTRACTION_FAILED,
				"Impossible d'extraire le texte du document.", exception);
		}
	}

	private void storeFile(Path storedFile, byte[] fileContent) {
		try {
			Files.createDirectories(storedFile.getParent());
			Files.write(storedFile, fileContent, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
		catch (IOException exception) {
			try {
				Files.deleteIfExists(storedFile);
			}
			catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			throw new CourseImportException(CourseImportException.Reason.FILE_STORAGE_FAILED,
				"Impossible de stocker le fichier du cours dans data/.", exception);
		}
	}
}
