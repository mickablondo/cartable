package dev.mickablondo.cartable.web;

import dev.mickablondo.cartable.course.CourseQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {

	private final CourseQueryService courseQueryService;

	HomeController(CourseQueryService courseQueryService) {
		this.courseQueryService = courseQueryService;
	}

	@GetMapping("/")
	String home(Model model) {
		model.addAttribute("courses", courseQueryService.findAll());
		model.addAttribute("subjects", SubjectOptions.all());
		model.addAttribute("subjectLabels", SubjectOptions.labels());
		return "home";
	}

}
