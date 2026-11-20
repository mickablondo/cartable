package dev.mickablondo.cartable.web;

import java.util.List;

import dev.mickablondo.cartable.course.CourseQueryService;
import dev.mickablondo.cartable.course.CourseStatus;
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
		var courses = courseQueryService.findAll();
		model.addAttribute("courses", courses);
		model.addAttribute("hasCoursesInProgress", courses.stream().anyMatch(course ->
			List.of(CourseStatus.IMPORTED, CourseStatus.TEXT_EXTRACTION_IN_PROGRESS, CourseStatus.VALIDATED)
				.contains(course.status())));
		model.addAttribute("subjects", SubjectOptions.all());
		model.addAttribute("subjectLabels", SubjectOptions.labels());
		return "home";
	}

}
