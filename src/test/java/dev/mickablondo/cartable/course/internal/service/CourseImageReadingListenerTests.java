package dev.mickablondo.cartable.course.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.CourseImageReadingRequested;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

class CourseImageReadingListenerTests {

	@Test
	void transcribesAndStoresEachImagePageInOrder(@TempDir Path dataDirectory) throws Exception {
		UUID courseId = UUID.randomUUID();
		String firstPagePath = "courses/%s/pages/0001.png".formatted(courseId);
		String secondPagePath = "courses/%s/pages/0002.png".formatted(courseId);
		byte[] firstImage = png(0x123456);
		byte[] secondImage = png(0x654321);
		writePage(dataDirectory, firstPagePath, firstImage);
		writePage(dataDirectory, secondPagePath, secondImage);

		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		stubCourseSource(jdbcTemplate, courseId, "pages.png", firstPagePath);
		stubPages(jdbcTemplate, firstPagePath, secondPagePath);
		when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any(), eq(courseId)))
			.thenReturn("Page 1\nPremière page\n\nPage 2\nDeuxième page");

		List<String> pageContents = new ArrayList<>();
		VisionTextClient fakeVisionClient = (prompt, image, mediaType) -> {
			assertThat(prompt).contains("Transcris exactement le texte visible");
			assertThat(mediaType).isEqualTo("image/png");
			pageContents.add(Arrays.equals(image, firstImage) ? "première" : "deuxième");
			return Arrays.equals(image, firstImage) ? "Première page" : "Deuxième page";
		};
		CourseImageReadingListener listener = new CourseImageReadingListener(
			jdbcTemplate,
			mock(PlatformTransactionManager.class),
			fakeVisionClient,
			dataDirectory,
			new ClassPathResource("prompts/image-transcription.st"),
			12_845_056,
			1.0f);

		listener.on(new CourseImageReadingRequested(courseId));

		assertThat(pageContents).containsExactly("première", "deuxième");
		verify(jdbcTemplate).update(
			contains("SET extracted_text = ?"), eq("Première page"), eq(courseId), eq(1));
		verify(jdbcTemplate).update(
			contains("SET extracted_text = ?"), eq("Deuxième page"), eq(courseId), eq(2));
		verify(jdbcTemplate).update(
			contains("SET extracted_text = ?, status = ?"),
			eq("Page 1\nPremière page\n\nPage 2\nDeuxième page"),
			eq("TEXT_TO_REVIEW"),
			eq(courseId));
	}

	private static void writePage(Path root, String relativePath, byte[] bytes) throws Exception {
		Path page = root.resolve(relativePath);
		Files.createDirectories(page.getParent());
		Files.write(page, bytes);
	}

	private static byte[] png(int color) throws Exception {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, color);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
		finally {
			image.flush();
		}
	}

	private static void stubCourseSource(JdbcTemplate jdbcTemplate, UUID courseId, String fileName, String sourcePath)
			throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getString("file_name")).thenReturn(fileName);
		when(resultSet.getString("source_path")).thenReturn(sourcePath);
		when(jdbcTemplate.queryForObject(
				anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(courseId)))
			.thenAnswer(invocation -> mapRow(invocation, resultSet));
	}

	private static void stubPages(JdbcTemplate jdbcTemplate, String... pagePaths) throws Exception {
		List<ResultSet> resultSets = new ArrayList<>();
		for (int index = 0; index < pagePaths.length; index++) {
			ResultSet resultSet = mock(ResultSet.class);
			when(resultSet.getInt("page_number")).thenReturn(index + 1);
			when(resultSet.getString("source_path")).thenReturn(pagePaths[index]);
			when(resultSet.getString("media_type")).thenReturn("image/png");
			when(resultSet.getBoolean("rendered_from_pdf")).thenReturn(false);
			resultSets.add(resultSet);
		}
		when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any()))
			.thenAnswer(invocation -> {
				RowMapper<?> mapper = invocation.getArgument(1);
				List<Object> pages = new ArrayList<>(resultSets.size());
				for (int index = 0; index < resultSets.size(); index++) {
					pages.add(mapper.mapRow(resultSets.get(index), index));
				}
				return pages;
			});
	}

	private static Object mapRow(InvocationOnMock invocation, ResultSet resultSet) throws Exception {
		RowMapper<?> mapper = invocation.getArgument(1);
		return mapper.mapRow(resultSet, 0);
	}

}
