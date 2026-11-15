package dev.mickablondo.cartable.web;

import dev.mickablondo.cartable.reference.ReferenceUpdateService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class ReferenceController {

	private final ReferenceUpdateService referenceUpdateService;

	ReferenceController(ReferenceUpdateService referenceUpdateService) {
		this.referenceUpdateService = referenceUpdateService;
	}

	@PostMapping("/references/update")
	String updateReference(RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("referenceUpdateReport", referenceUpdateService.updateReference());
		return "redirect:/";
	}

}
