package dev.mickablondo.cartable.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mickablondo.cartable.course.CourseImportResult;
import dev.mickablondo.cartable.course.CourseImportService;
import dev.mickablondo.cartable.course.CourseQueryService;
import dev.mickablondo.cartable.course.CourseStatus;
import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.NoCoursePassagesFoundException;
import dev.mickablondo.cartable.sheet.Sheet;
import dev.mickablondo.cartable.sheet.SheetCourseContent;
import dev.mickablondo.cartable.sheet.SheetDefinition;
import dev.mickablondo.cartable.sheet.SheetGenerationService;
import dev.mickablondo.cartable.sheet.SheetGeneralKnowledge;
import dev.mickablondo.cartable.sheet.SheetKnowledgeOrigin;
import dev.mickablondo.cartable.sheet.SheetMarkdownExportService;
import dev.mickablondo.cartable.sheet.SheetQueryService;
import dev.mickablondo.cartable.sheet.SheetSource;
import dev.mickablondo.cartable.sheet.SheetStatement;
import dev.mickablondo.cartable.sheet.SheetStatus;
import dev.mickablondo.cartable.sheet.SheetVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({ HomeController.class, CourseController.class, SheetController.class })
class CourseAndSheetControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CourseImportService courseImportService;

	@MockitoBean
	private CourseQueryService courseQueryService;

	@MockitoBean
	private SheetGenerationService sheetGenerationService;

	@MockitoBean
	private SheetQueryService sheetQueryService;

	@MockitoBean
	private SheetVerificationService sheetVerificationService;

	@MockitoBean
	private SheetMarkdownExportService sheetMarkdownExportService;

	@Test
	void importsCourseWithRequiredSubjectAndDeletesTemporaryUpload() throws Exception {
		when(courseImportService.importCourse(anyList(), eq(Subject.MATHEMATICS)))
			.thenReturn(new CourseImportResult(
				UUID.randomUUID(), "maths.docx", Subject.MATHEMATICS, Instant.now(),
				CourseStatus.IMPORTED, false));
		MockMultipartFile file = new MockMultipartFile(
			"file", "maths.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"contenu de test".getBytes());

		mockMvc.perform(multipart("/courses").file(file).param("subject", "MATHEMATICS"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		var uploadedPaths = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(courseImportService).importCourse(uploadedPaths.capture(), eq(Subject.MATHEMATICS));
		Path uploadedPath = (Path) uploadedPaths.getValue().getFirst();
		assertEquals("maths.docx", uploadedPath.getFileName().toString());
		assertFalse(Files.exists(uploadedPath));
		assertFalse(Files.exists(uploadedPath.getParent()));
	}

	@Test
	void importsMultipleImagePagesInSelectionOrder() throws Exception {
		when(courseImportService.importCourse(anyList(), eq(Subject.MATHEMATICS)))
			.thenReturn(new CourseImportResult(
				UUID.randomUUID(), "page-1.jpg", Subject.MATHEMATICS, Instant.now(),
				CourseStatus.IMPORTED, true));
		MockMultipartFile firstPage = new MockMultipartFile("file", "page-1.jpg", "image/jpeg", new byte[] { 1 });
		MockMultipartFile secondPage = new MockMultipartFile("file", "page-2.png", "image/png", new byte[] { 2 });

		mockMvc.perform(multipart("/courses")
				.file(firstPage)
				.file(secondPage)
				.param("subject", "MATHEMATICS"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"))
			.andExpect(flash().attribute("importSuccess",
				"Le cours a été importé. La lecture des images reste à effectuer."));

		var uploadedPaths = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(courseImportService).importCourse(uploadedPaths.capture(), eq(Subject.MATHEMATICS));
		List<?> paths = uploadedPaths.getValue();
		assertEquals("page-1.jpg", ((Path) paths.get(0)).getFileName().toString());
		assertEquals("page-2.png", ((Path) paths.get(1)).getFileName().toString());
		for (Object path : paths) {
			Path uploadedPath = (Path) path;
			assertFalse(Files.exists(uploadedPath));
			assertFalse(Files.exists(uploadedPath.getParent()));
		}
	}

	@Test
	void refusesCourseImportWithoutSubject() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "cours.pdf", "application/pdf", new byte[] { 1 });

		mockMvc.perform(multipart("/courses").file(file))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		verify(courseImportService, never()).importCourse(anyList(), any());
	}

	@Test
	void displaysSheetRequestFormWithFrenchSubjectChoices() throws Exception {
		mockMvc.perform(get("/sheets/new"))
			.andExpect(status().isOk())
			.andExpect(view().name("sheet-form"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Matière")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Mathématiques")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Thème")));
	}

	@Test
	void generatesSheetOnlyWhenSubjectAndThemeAreProvided() throws Exception {
		Sheet sheet = sampleSheet();
		when(sheetGenerationService.generate("Géométrie", Subject.MATHEMATICS)).thenReturn(sheet);

		mockMvc.perform(post("/sheets").param("subject", "MATHEMATICS").param("theme", " Géométrie "))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/sheets/" + sheet.id()));

		mockMvc.perform(post("/sheets").param("subject", "MATHEMATICS").param("theme", " "))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/sheets/new"));

		verify(sheetGenerationService).generate("Géométrie", Subject.MATHEMATICS);
		verify(sheetGenerationService, never()).generate("", Subject.MATHEMATICS);
	}

	@Test
	void reportsWhenNoCoursePassageIsAvailable() throws Exception {
		when(sheetGenerationService.generate("Géométrie", Subject.MATHEMATICS))
			.thenThrow(new NoCoursePassagesFoundException());

		mockMvc.perform(post("/sheets").param("subject", "MATHEMATICS").param("theme", "Géométrie"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/sheets/new"))
			.andExpect(flash().attribute("formError",
				"Aucun passage de cours n'a été trouvé pour ce thème et cette matière. "
					+ "Importez ou indexez d'abord un cours correspondant."));

		mockMvc.perform(get("/sheets/new").flashAttrs(java.util.Map.of(
				"formError", "Aucun passage de cours n'a été trouvé pour ce thème et cette matière.")))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString(
				"Aucun passage de cours")));
	}

	@Test
	void displaysSheetWithSourcesAndValidationAction() throws Exception {
		Sheet sheet = sampleSheet();
		when(sheetQueryService.findById(sheet.id())).thenReturn(Optional.of(sheet));

		mockMvc.perform(get("/sheets/{id}", sheet.id()))
			.andExpect(status().isOk())
			.andExpect(view().name("sheet-detail"))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("À vérifier")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Géométrie plane")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("GENERAL_KNOWLEDGE")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Complément de connaissances générales")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("cours-maths.pdf")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Chapitre 2")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.test/geometry")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("2025-09-01")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Valider")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Exporter en Markdown")));
	}

	@Test
	void validatesSheetAndExportsMarkdownAsDownload() throws Exception {
		Sheet sheet = sampleSheet();
		when(sheetQueryService.findById(sheet.id())).thenReturn(Optional.of(sheet));
		when(sheetMarkdownExportService.exportMarkdown(sheet.id())).thenReturn("# Géométrie plane\n");

		mockMvc.perform(post("/sheets/{id}/verify", sheet.id()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/sheets/" + sheet.id()));
		verify(sheetVerificationService).verify(sheet.id());

		mockMvc.perform(get("/sheets/{id}/export", sheet.id()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("fiche-" + sheet.id() + ".md")))
			.andExpect(content().contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8")))
			.andExpect(content().string("# Géométrie plane\n"));
		verify(sheetMarkdownExportService).exportMarkdown(sheet.id());
	}

	private static Sheet sampleSheet() {
		SheetSource courseSource = new SheetSource(
			"C1", "COURSE", "cours-maths.pdf", "Chapitre 2", null, null, null);
		SheetSource referenceSource = new SheetSource(
			"R1", "OFFICIAL_REFERENCE", "programme.pdf", "Géométrie", "Programme de mathématiques",
			"https://example.test/geometry", java.time.LocalDate.parse("2025-09-01"));
		return new Sheet(
			UUID.fromString("6a7dce15-8fb2-4c64-b9f0-d20c470e32d4"),
			Subject.MATHEMATICS,
			"Géométrie plane",
			new SheetCourseContent(
				List.of(new SheetStatement("Les angles d'un triangle ont une somme de 180°.", List.of("C1"))),
				List.of(new SheetDefinition("Triangle", "Polygone à trois côtés.", List.of("C1"))),
				List.of()),
			List.of(new SheetGeneralKnowledge(
				"Un triangle peut aussi être classé selon ses côtés.", SheetKnowledgeOrigin.GENERAL_KNOWLEDGE)),
			List.of(),
			List.of(courseSource, referenceSource),
			SheetStatus.TO_VERIFY,
			Instant.parse("2026-11-15T10:00:00Z"));
	}

}
