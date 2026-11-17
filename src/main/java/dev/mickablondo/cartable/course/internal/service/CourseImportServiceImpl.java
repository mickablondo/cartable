package dev.mickablondo.cartable.course.internal.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mickablondo.cartable.course.CourseImportException;
import dev.mickablondo.cartable.course.CourseImportResult;
import dev.mickablondo.cartable.course.CourseImageReadingRequested;
import dev.mickablondo.cartable.course.CourseImportService;
import dev.mickablondo.cartable.course.CourseImported;
import dev.mickablondo.cartable.course.CourseStatus;
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

	private static final CourseStatus IMPORTED_STATUS = CourseStatus.IMPORTED;

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
		return importCourse(file == null ? null : List.of(file), subject);
	}

	@Override
	public CourseImportResult importCourse(List<Path> files, Subject subject) {
		if (subject == null) {
			throw new IllegalArgumentException("La matière est obligatoire.");
		}
		if (files == null || files.isEmpty()) {
			throw new CourseImportException(CourseImportException.Reason.NO_FILES,
				"Choisissez au moins un fichier de cours.");
		}

		List<ImportFile> importFiles = new ArrayList<>(files.size());
		for (Path file : files) {
			if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
				throw new CourseImportException(CourseImportException.Reason.FILE_NOT_READABLE,
					"Le fichier du cours est inaccessible.");
			}

			String fileName = file.getFileName().toString();
			String extension = supportedExtension(fileName);
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
			importFiles.add(new ImportFile(fileName, extension, fileContent));
		}

		boolean imageImport = importFiles.getFirst().isImage();
		if (importFiles.size() > 1 && importFiles.stream().anyMatch(file -> !file.isImage())) {
			throw new CourseImportException(CourseImportException.Reason.UNSUPPORTED_FORMAT,
				"Pour un cours en plusieurs pages, sélectionnez uniquement des images.");
		}
		if (importFiles.stream().anyMatch(file -> file.isImage() != imageImport)) {
			throw new CourseImportException(CourseImportException.Reason.UNSUPPORTED_FORMAT,
				"Un cours ne peut pas mélanger des images et un document PDF ou Word.");
		}

		String extractedText = "";
		boolean requiresImageReading = imageImport;
		if (!imageImport) {
			ImportFile importFile = importFiles.getFirst();
			extractedText = extractText(importFile.content(), importFile.fileName());
			if (extractedText.isBlank()) {
				if (importFile.extension().equals(".pdf")) {
					requiresImageReading = true;
				}
				else {
					throw new CourseImportException(CourseImportException.Reason.NO_TEXT_EXTRACTED,
						"Aucun texte n'a été extrait du document Word.");
				}
			}
		}

		UUID courseId = UUID.randomUUID();
		Instant importedAt = Instant.now();
		List<StoredFile> storedFiles = new ArrayList<>(importFiles.size());
		for (int index = 0; index < importFiles.size(); index++) {
			ImportFile importFile = importFiles.get(index);
			String relativeFilePath = imageImport
				? "courses/" + courseId + "/pages/%04d%s".formatted(index + 1, importFile.extension())
				: "courses/" + courseId + importFile.extension();
			Path storedFile = dataDirectory.resolve(relativeFilePath).normalize();
			storedFiles.add(new StoredFile(importFile, index + 1, relativeFilePath, storedFile));
		}

		try {
			for (StoredFile storedFile : storedFiles) {
				storeFile(storedFile.path(), storedFile.importFile().content());
			}
		}
		catch (RuntimeException exception) {
			deleteStoredFiles(storedFiles, exception);
			throw exception;
		}

		String fileName = importFiles.getFirst().fileName();
		String relativeFilePath = storedFiles.getFirst().relativePath();
		String textToStore = extractedText;
		boolean imageReadingRequired = requiresImageReading;
		CourseImportResult result = new CourseImportResult(
			courseId, fileName, subject, importedAt, IMPORTED_STATUS, imageReadingRequired);
		try {
			return transactionTemplate.execute(status -> {
				jdbcTemplate.update("""
					INSERT INTO course (
						id, file_name, subject, imported_at, status, source_path, extracted_text, requires_image_reading
					)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""",
					courseId,
					fileName,
					subject.name(),
					Timestamp.from(importedAt),
					IMPORTED_STATUS.name(),
					relativeFilePath,
					textToStore,
					imageReadingRequired);
				if (imageImport) {
					for (StoredFile storedFile : storedFiles) {
						jdbcTemplate.update("""
							INSERT INTO course_page (course_id, page_number, file_name, source_path, media_type)
							VALUES (?, ?, ?, ?, ?)
							""",
							courseId,
							storedFile.pageNumber(),
							storedFile.importFile().fileName(),
							storedFile.relativePath(),
							mediaType(storedFile.importFile().extension()));
					}
				}
				if (imageReadingRequired) {
					events.publishEvent(new CourseImageReadingRequested(courseId));
				}
				else {
					events.publishEvent(new CourseImported(courseId));
				}
				return result;
			});
		}
		catch (RuntimeException exception) {
			deleteStoredFiles(storedFiles, exception);
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
		if (!List.of(".pdf", ".doc", ".docx", ".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
			throw new CourseImportException(CourseImportException.Reason.UNSUPPORTED_FORMAT,
				"Format non supporté : seuls les fichiers PDF, Word (.doc, .docx), JPEG, PNG et WebP sont acceptés.");
		}
		return extension;
	}

	private static String mediaType(String extension) {
		return switch (extension) {
			case ".jpg", ".jpeg" -> "image/jpeg";
			case ".png" -> "image/png";
			case ".webp" -> "image/webp";
			default -> throw new IllegalArgumentException("Extension d'image inattendue : " + extension);
		};
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

	private static void deleteStoredFiles(List<StoredFile> storedFiles, RuntimeException cause) {
		for (StoredFile storedFile : storedFiles) {
			try {
				Files.deleteIfExists(storedFile.path());
			}
			catch (IOException exception) {
				cause.addSuppressed(exception);
			}
		}
	}

	private record ImportFile(String fileName, String extension, byte[] content) {

		private boolean isImage() {
			return extension.equals(".jpg") || extension.equals(".jpeg")
				|| extension.equals(".png") || extension.equals(".webp");
		}

	}

	private record StoredFile(ImportFile importFile, int pageNumber, String relativePath, Path path) {
	}
}
