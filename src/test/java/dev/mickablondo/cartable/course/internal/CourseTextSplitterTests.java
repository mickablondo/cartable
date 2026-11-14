package dev.mickablondo.cartable.course.internal;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseTextSplitterTests {

	private final CourseTextSplitter splitter = new CourseTextSplitter();

	@Test
	void usesExplicitTitlesAsChapterMetadata() {
		List<CourseTextSplitter.Chunk> chunks = splitter.split(
			"Chapitre 1 - Test\nPremière notion.\nPartie 2 - Test\nDeuxième notion.");

		assertEquals(2, chunks.size());
		assertEquals("Chapitre 1 - Test", chunks.get(0).chapter());
		assertEquals("Partie 2 - Test", chunks.get(1).chapter());
		assertEquals("Première notion.", chunks.get(0).text());
		assertEquals("Deuxième notion.", chunks.get(1).text());
	}

	@Test
	void fallsBackToUnlabelledChunksWhenNoExplicitTitleExists() {
		List<CourseTextSplitter.Chunk> chunks = splitter.split("Texte sans titre.");

		assertEquals(1, chunks.size());
		assertEquals("Sans chapitre détecté", chunks.getFirst().chapter());
		assertEquals("Texte sans titre.", chunks.getFirst().text());
	}

	@Test
	void capsLongPassagesAndIncludesOverlappingText() {
		String text = IntStream.range(0, 400)
			.mapToObj("mot%03d "::formatted)
			.collect(Collectors.joining());
		List<CourseTextSplitter.Chunk> chunks = splitter.split(text);

		assertTrue(chunks.size() > 1);
		assertTrue(chunks.stream().allMatch(chunk ->
			chunk.text().length() <= CourseTextSplitter.MAX_CHUNK_CHARACTERS));
		String first = chunks.get(0).text();
		String second = chunks.get(1).text();
		int overlapLength = Math.min(first.length(), second.length());
		while (overlapLength > 0 && !first.endsWith(second.substring(0, overlapLength))) {
			overlapLength--;
		}
		assertTrue(overlapLength >= 150);
		assertFalse(chunks.get(1).text().isBlank());
	}
}
