package dev.mickablondo.cartable.reference.internal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ReferenceTextSplitter {

	private static final int MAX_CHUNK_CHARACTERS = 1_200;
	private static final int OVERLAP_CHARACTERS = 150;
	private static final String NO_CHAPTER = "Sans chapitre détecté";
	private static final Pattern CHAPTER_TITLE = Pattern.compile(
		"^(?:chapitre|partie|thème|theme|séquence|sequence|dossier)(?:\\b|\\s|[:.-]).*$",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	List<Chunk> split(String text) {
		String normalizedText = text.replace("\r\n", "\n").replace('\r', '\n').strip();
		if (normalizedText.isEmpty()) {
			return List.of();
		}

		String[] lines = normalizedText.split("\n", -1);
		boolean hasChapterTitles = false;
		for (String line : lines) {
			if (isChapterTitle(line.strip())) {
				hasChapterTitles = true;
				break;
			}
		}
		if (!hasChapterTitles) {
			return splitSection(normalizedText, NO_CHAPTER);
		}

		List<Chunk> chunks = new ArrayList<>();
		String chapter = NO_CHAPTER;
		StringBuilder section = new StringBuilder();
		boolean hasPreviousLine = false;
		for (String line : lines) {
			String trimmedLine = line.strip();
			if (isChapterTitle(trimmedLine)) {
				addSection(chunks, section, chapter);
				chapter = trimmedLine;
				hasPreviousLine = false;
			}
			else {
				if (hasPreviousLine) {
					section.append('\n');
				}
				if (!trimmedLine.isEmpty()) {
					section.append(trimmedLine);
				}
				hasPreviousLine = true;
			}
		}
		addSection(chunks, section, chapter);
		return List.copyOf(chunks);
	}

	private static void addSection(List<Chunk> chunks, StringBuilder section, String chapter) {
		if (!section.isEmpty()) {
			chunks.addAll(splitSection(section.toString(), chapter));
			section.setLength(0);
		}
	}

	private static List<Chunk> splitSection(String text, String chapter) {
		String remaining = text.strip();
		List<Chunk> chunks = new ArrayList<>();
		while (remaining.length() > MAX_CHUNK_CHARACTERS) {
			int end = findBoundary(remaining);
			chunks.add(new Chunk(remaining.substring(0, end).strip(), chapter));

			int overlapStart = end - OVERLAP_CHARACTERS;
			while (overlapStart > 0 && !Character.isWhitespace(remaining.charAt(overlapStart - 1))) {
				overlapStart--;
			}
			while (overlapStart < end && Character.isWhitespace(remaining.charAt(overlapStart))) {
				overlapStart++;
			}
			if (overlapStart == 0) {
				overlapStart = end;
			}
			remaining = remaining.substring(overlapStart).stripLeading();
		}
		if (!remaining.isBlank()) {
			chunks.add(new Chunk(remaining, chapter));
		}
		return chunks;
	}

	private static int findBoundary(String text) {
		int minimumBoundary = MAX_CHUNK_CHARACTERS / 2;
		int paragraphBoundary = text.lastIndexOf("\n\n", MAX_CHUNK_CHARACTERS - 1);
		if (paragraphBoundary >= minimumBoundary) {
			return paragraphBoundary;
		}
		for (int index = MAX_CHUNK_CHARACTERS; index >= minimumBoundary; index--) {
			if (Character.isWhitespace(text.charAt(index))) {
				return index;
			}
		}
		return MAX_CHUNK_CHARACTERS;
	}

	private static boolean isChapterTitle(String line) {
		return CHAPTER_TITLE.matcher(line).matches();
	}

	record Chunk(String text, String chapter) {
	}

}
