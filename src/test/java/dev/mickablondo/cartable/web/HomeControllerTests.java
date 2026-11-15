package dev.mickablondo.cartable.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.CourseQueryService;
import dev.mickablondo.cartable.course.CourseSummary;
import dev.mickablondo.cartable.course.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
class HomeControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CourseQueryService courseQueryService;

	@Test
	void displaysCourseImportFormAndEmptyCourseList() throws Exception {
		when(courseQueryService.findAll()).thenReturn(List.of());

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Importer un cours")))
			.andExpect(content().string(containsString("Matière")))
			.andExpect(content().string(containsString("Cours importés")))
			.andExpect(content().string(containsString("Mettre à jour le référentiel")))
			.andExpect(content().string(containsString("Aucun cours importé")));
	}

	@Test
	void displaysImportedCourseAndItsIndexingStatus() throws Exception {
		when(courseQueryService.findAll()).thenReturn(List.of(
			new CourseSummary(UUID.randomUUID(), "cours.docx", Subject.FRENCH, Instant.parse("2026-11-15T10:00:00Z"),
				"INDEXED")));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("cours.docx")))
			.andExpect(content().string(containsString("Français")))
			.andExpect(content().string(containsString("Indexé")));
	}

}
