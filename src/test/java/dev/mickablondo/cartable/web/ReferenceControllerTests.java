package dev.mickablondo.cartable.web;

import java.util.List;

import dev.mickablondo.cartable.reference.ReferenceUpdateIssue;
import dev.mickablondo.cartable.reference.ReferenceUpdateReport;
import dev.mickablondo.cartable.reference.ReferenceUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReferenceController.class)
class ReferenceControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReferenceUpdateService referenceUpdateService;

	@Test
	void updatesReferenceOnExplicitActionAndReportsIssues() throws Exception {
		ReferenceUpdateReport report = new ReferenceUpdateReport(
			1, 2, 3,
			List.of(new ReferenceUpdateIssue(
				"programme",
				"https://example.test/programme.pdf",
				"HTTP 403. Déposez le PDF dans data/reference/programme.pdf.",
				false)));
		when(referenceUpdateService.updateReference()).thenReturn(report);

		mockMvc.perform(post("/references/update"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"))
			.andExpect(flash().attribute("referenceUpdateReport", report));

		verify(referenceUpdateService).updateReference();
	}

}
