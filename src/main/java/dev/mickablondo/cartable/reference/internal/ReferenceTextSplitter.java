package dev.mickablondo.cartable.reference.internal;

import java.util.ArrayList;
import java.util.List;

final class ReferenceTextSplitter {

	private static final int MAX_CHUNK_CHARACTERS = 1_200;
	private static final int OVERLAP_CHARACTERS = 150;

	List<String> split(String text) {
		String remaining = text.replace("\r\n", "\n").replace('\r', '\n').strip();
		List<String> chunks = new ArrayList<>();
		while (remaining.length() > MAX_CHUNK_CHARACTERS) {
			int end = findBoundary(remaining);
			chunks.add(remaining.substring(0, end).strip());

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
			chunks.add(remaining);
		}
		return List.copyOf(chunks);
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

}
