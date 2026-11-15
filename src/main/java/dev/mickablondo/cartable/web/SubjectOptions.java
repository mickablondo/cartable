package dev.mickablondo.cartable.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mickablondo.cartable.course.Subject;

final class SubjectOptions {

	private static final List<SubjectOption> OPTIONS = List.of(
		new SubjectOption(Subject.MATHEMATICS, "Mathématiques"),
		new SubjectOption(Subject.FRENCH, "Français"),
		new SubjectOption(Subject.HISTORY_GEOGRAPHY, "Histoire-géographie"),
		new SubjectOption(Subject.PHYSICS_CHEMISTRY, "Sciences physiques"),
		new SubjectOption(Subject.LIFE_AND_EARTH_SCIENCES, "SVT"),
		new SubjectOption(Subject.TECHNOLOGY, "Technologie"));
	private static final Map<Subject, String> LABELS = OPTIONS.stream()
		.collect(Collectors.toUnmodifiableMap(SubjectOption::value, SubjectOption::label));

	private SubjectOptions() {
	}

	static List<SubjectOption> all() {
		return OPTIONS;
	}

	static String label(Subject subject) {
		String label = LABELS.get(subject);
		if (label == null) {
			throw new IllegalArgumentException("Matière inconnue : " + subject);
		}
		return label;
	}

	static Map<Subject, String> labels() {
		return LABELS;
	}

}
