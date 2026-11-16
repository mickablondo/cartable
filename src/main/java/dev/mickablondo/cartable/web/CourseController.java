package dev.mickablondo.cartable.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.mickablondo.cartable.course.CourseImportException;
import dev.mickablondo.cartable.course.CourseImportResult;
import dev.mickablondo.cartable.course.CourseImportService;
import dev.mickablondo.cartable.course.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class CourseController {

	private static final Logger logger = LoggerFactory.getLogger(CourseController.class);

	private final CourseImportService courseImportService;

	CourseController(CourseImportService courseImportService) {
		this.courseImportService = courseImportService;
	}

	@PostMapping("/courses")
	String importCourse(
			@RequestParam(value = "file", required = false) List<MultipartFile> files,
			@RequestParam(value = "subject", required = false) Subject subject,
			RedirectAttributes redirectAttributes) {
		if (files == null || files.isEmpty() || files.stream().anyMatch(MultipartFile::isEmpty)) {
			redirectAttributes.addFlashAttribute("importError", "Choisissez au moins un fichier de cours.");
			return "redirect:/";
		}
		if (subject == null) {
			redirectAttributes.addFlashAttribute("importError", "Choisissez la matière du cours.");
			return "redirect:/";
		}

		List<Path> temporaryFiles = new ArrayList<>();
		try {
			for (MultipartFile file : files) {
				String fileName = safeFileName(file.getOriginalFilename());
				if (fileName == null) {
					redirectAttributes.addFlashAttribute("importError", "Le nom d'un fichier est invalide.");
					return "redirect:/";
				}

				Path temporaryDirectory = Files.createTempDirectory("cartable-course-upload-");
				Path temporaryFile = temporaryDirectory.resolve(fileName);
				temporaryFiles.add(temporaryFile);
				try (var inputStream = file.getInputStream()) {
					Files.copy(inputStream, temporaryFile);
				}
			}

			CourseImportResult result = courseImportService.importCourse(
				temporaryFiles, subject);
			redirectAttributes.addFlashAttribute("importSuccess",
				result.requiresImageReading()
					? "Le cours a été importé. La lecture des images reste à effectuer."
					: "Le cours a été importé.");
		}
		catch (CourseImportException exception) {
			redirectAttributes.addFlashAttribute("importError", exception.getMessage());
		}
		catch (IOException exception) {
			redirectAttributes.addFlashAttribute(
				"importError", "Impossible de lire le fichier transmis. Réessayez avec un autre fichier.");
			logger.warn("Impossible de traiter le fichier de cours téléversé.", exception);
		}
		finally {
			for (Path temporaryFile : temporaryFiles) {
				deleteTemporaryUpload(temporaryFile, temporaryFile.getParent());
			}
		}
		return "redirect:/";
	}

	private static String safeFileName(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			return null;
		}
		String normalized = originalFilename.replace('\\', '/');
		String fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
			.replaceAll("[<>:\"|?*\\p{Cntrl}]", "_")
			.replaceAll("[. ]+$", "");
		return fileName.isBlank() || fileName.equals(".") || fileName.equals("..") ? null : fileName;
	}

	private static void deleteTemporaryUpload(Path temporaryFile, Path temporaryDirectory) {
		try {
			if (temporaryFile != null) {
				Files.deleteIfExists(temporaryFile);
			}
			if (temporaryDirectory != null) {
				Files.deleteIfExists(temporaryDirectory);
			}
		}
		catch (IOException exception) {
			logger.error("Impossible de supprimer le fichier temporaire de téléversement.", exception);
		}
	}

}
