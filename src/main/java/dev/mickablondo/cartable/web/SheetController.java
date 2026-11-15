package dev.mickablondo.cartable.web;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetGenerationService;
import dev.mickablondo.cartable.sheet.SheetMarkdownExportService;
import dev.mickablondo.cartable.sheet.SheetQueryService;
import dev.mickablondo.cartable.sheet.SheetStatus;
import dev.mickablondo.cartable.sheet.SheetVerificationService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class SheetController {

	private final SheetGenerationService sheetGenerationService;
	private final SheetQueryService sheetQueryService;
	private final SheetVerificationService sheetVerificationService;
	private final SheetMarkdownExportService sheetMarkdownExportService;

	SheetController(
			SheetGenerationService sheetGenerationService,
			SheetQueryService sheetQueryService,
			SheetVerificationService sheetVerificationService,
			SheetMarkdownExportService sheetMarkdownExportService) {
		this.sheetGenerationService = sheetGenerationService;
		this.sheetQueryService = sheetQueryService;
		this.sheetVerificationService = sheetVerificationService;
		this.sheetMarkdownExportService = sheetMarkdownExportService;
	}

	@GetMapping("/sheets/new")
	String newSheet(Model model) {
		model.addAttribute("subjects", SubjectOptions.all());
		return "sheet-form";
	}

	@PostMapping("/sheets")
	String generate(
			@RequestParam(value = "subject", required = false) String subjectValue,
			@RequestParam(value = "theme", required = false) String theme,
			RedirectAttributes redirectAttributes) {
		Subject subject;
		try {
			subject = subjectValue == null ? null : Subject.valueOf(subjectValue);
		}
		catch (IllegalArgumentException exception) {
			subject = null;
		}
		if (subject == null) {
			redirectAttributes.addFlashAttribute("formError", "Choisissez une matière.");
			return "redirect:/sheets/new";
		}
		if (theme == null || theme.isBlank()) {
			redirectAttributes.addFlashAttribute("formError", "Saisissez un thème de révision.");
			return "redirect:/sheets/new";
		}

		Sheet sheet = sheetGenerationService.generate(theme.strip(), subject);
		return "redirect:/sheets/" + sheet.id();
	}

	@GetMapping("/sheets/{sheetId}")
	String show(@PathVariable UUID sheetId, Model model) {
		Sheet sheet = findSheet(sheetId);
		model.addAttribute("sheet", sheet);
		model.addAttribute("subjectLabel", SubjectOptions.label(sheet.subject()));
		model.addAttribute("needsVerification", sheet.status() == SheetStatus.TO_VERIFY);
		return "sheet-detail";
	}

	@PostMapping("/sheets/{sheetId}/verify")
	String verify(@PathVariable UUID sheetId) {
		findSheet(sheetId);
		sheetVerificationService.verify(sheetId);
		return "redirect:/sheets/" + sheetId;
	}

	@GetMapping("/sheets/{sheetId}/export")
	ResponseEntity<byte[]> exportMarkdown(@PathVariable UUID sheetId) {
		findSheet(sheetId);
		byte[] markdown = sheetMarkdownExportService.exportMarkdown(sheetId).getBytes(StandardCharsets.UTF_8);
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename("fiche-" + sheetId + ".md").build().toString())
			.body(markdown);
	}

	private Sheet findSheet(UUID sheetId) {
		return sheetQueryService.findById(sheetId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche introuvable."));
	}

}
