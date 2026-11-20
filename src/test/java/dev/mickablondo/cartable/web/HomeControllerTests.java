package dev.mickablondo.cartable.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.CourseQueryService;
import dev.mickablondo.cartable.course.CourseSummary;
import dev.mickablondo.cartable.course.CourseStatus;
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
import static org.hamcrest.Matchers.not;

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
			.andExpect(content().string(containsString("Aucun cours importé")))
			.andExpect(content().string(containsString("Choisissez un PDF ou Word")))
			.andExpect(content().string(containsString("Monter")))
			.andExpect(content().string(containsString("newlySelectedFiles")))
			.andExpect(content().string(containsString("[...selectedFiles, ...newlySelectedFiles]")))
			.andExpect(content().string(containsString("required")))
			.andExpect(content().string(containsString("multiple")))
			.andExpect(content().string(containsString(".webp")));
	}

	@Test
	void displaysImportedCourseAndItsIndexingStatus() throws Exception {
		when(courseQueryService.findAll()).thenReturn(List.of(
			new CourseSummary(UUID.randomUUID(), "cours.docx", Subject.FRENCH, Instant.parse("2026-11-15T10:00:00Z"),
				CourseStatus.INDEXED, false)));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("cours.docx")))
			.andExpect(content().string(containsString("Français")))
			.andExpect(content().string(containsString("Indexé")));
	}

	@Test
	void displaysWhenCourseRequiresImageReading() throws Exception {
		when(courseQueryService.findAll()).thenReturn(List.of(
			new CourseSummary(UUID.randomUUID(), "page-1.jpg", Subject.FRENCH,
				Instant.parse("2026-11-15T10:00:00Z"), CourseStatus.IMPORTED, true)));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lecture d’images requise")));
	}

	@Test
	void offersReviewForTranscribedImageCourse() throws Exception {
		UUID courseId = UUID.randomUUID();
		when(courseQueryService.findAll()).thenReturn(List.of(
			new CourseSummary(courseId, "page-1.jpg", Subject.FRENCH,
				Instant.parse("2026-11-15T10:00:00Z"), CourseStatus.TEXT_TO_REVIEW, false)));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Relire et valider le texte")))
			.andExpect(content().string(containsString("/courses/" + courseId + "/review")));
	}

	@Test
	void refreshesHomeWhileCourseProcessingIsInProgress() throws Exception {
		when(courseQueryService.findAll()).thenReturn(List.of(
			new CourseSummary(UUID.randomUUID(), "scan.pdf", Subject.FRENCH,
				Instant.parse("2026-11-15T10:00:00Z"), CourseStatus.TEXT_EXTRACTION_IN_PROGRESS, true)));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("http-equiv=\"refresh\"")))
			.andExpect(content().string(containsString("content=\"10\"")))
			.andExpect(content().string(containsString("s’actualise automatiquement")));
	}

	@Test
	void doesNotRefreshHomeWhenNoCourseIsProcessing() throws Exception {
		when(courseQueryService.findAll()).thenReturn(List.of(
			new CourseSummary(UUID.randomUUID(), "cours.docx", Subject.FRENCH,
				Instant.parse("2026-11-15T10:00:00Z"), CourseStatus.INDEXED, false)));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("http-equiv=\"refresh\""))));
	}

}
