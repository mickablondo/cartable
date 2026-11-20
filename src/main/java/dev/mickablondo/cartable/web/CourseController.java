package dev.mickablondo.cartable.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.CourseImportException;
import dev.mickablondo.cartable.course.CourseImportResult;
import dev.mickablondo.cartable.course.CourseImportService;
import dev.mickablondo.cartable.course.CoursePageImage;
import dev.mickablondo.cartable.course.CoursePageReview;
import dev.mickablondo.cartable.course.CourseReviewException;
import dev.mickablondo.cartable.course.CourseReviewService;
import dev.mickablondo.cartable.course.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class CourseController {

	private static final Logger logger = LoggerFactory.getLogger(CourseController.class);

	private final CourseImportService courseImportService;
	private final CourseReviewService courseReviewService;

	CourseController(CourseImportService courseImportService, CourseReviewService courseReviewService) {
		this.courseImportService = courseImportService;
		this.courseReviewService = courseReviewService;
	}

	@GetMapping("/courses/{courseId}/review")
	String reviewCourse(
			@PathVariable UUID courseId,
			Model model,
			RedirectAttributes redirectAttributes) {
		try {
			List<CoursePageReview> pages = courseReviewService.findPages(courseId);
			model.addAttribute("courseId", courseId);
			model.addAttribute("pages", pages);
			return "course-review";
		}
		catch (CourseReviewException exception) {
			redirectAttributes.addFlashAttribute("reviewError", exception.getMessage());
			return "redirect:/";
		}
	}

	@GetMapping("/courses/{courseId}/pages/{pageNumber}/image")
	ResponseEntity<byte[]> pageImage(@PathVariable UUID courseId, @PathVariable int pageNumber) {
		CoursePageImage image = courseReviewService.findPageImage(courseId, pageNumber);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(image.mediaType()))
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(image.content());
	}

	@PostMapping("/courses/{courseId}/pages/{pageNumber}")
	String updatePageText(
			@PathVariable UUID courseId,
			@PathVariable int pageNumber,
			@RequestParam(value = "text", defaultValue = "") String text,
			RedirectAttributes redirectAttributes) {
		try {
			courseReviewService.updatePageText(courseId, pageNumber, text);
			redirectAttributes.addFlashAttribute("reviewSuccess", "Le texte de la page a été enregistré.");
		}
		catch (CourseReviewException exception) {
			redirectAttributes.addFlashAttribute("reviewError", exception.getMessage());
		}
		return "redirect:/courses/" + courseId + "/review";
	}

	@PostMapping("/courses/{courseId}/validate")
	String validateCourse(@PathVariable UUID courseId, RedirectAttributes redirectAttributes) {
		try {
			courseReviewService.validate(courseId);
			redirectAttributes.addFlashAttribute(
				"reviewSuccess", "Le texte a été validé. Le cours va être indexé.");
		}
		catch (CourseReviewException exception) {
			redirectAttributes.addFlashAttribute("reviewError", exception.getMessage());
		}
		return "redirect:/";
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
