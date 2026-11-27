package dev.mickablondo.cartable.sheet;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.mickablondo.cartable.course.Subject;

/**
 * Fiche spécialisée d'histoire-géographie.
 */
public record HistoryGeographySheet(
		UUID id,
		Subject subject,
		String theme,
		String sheetType,
		int schemaVersion,
		List<SheetStatement> keyConcepts,
		List<HistoryGeographyEvent> chronology,
		List<HistoryGeographyCharacter> characters,
		List<SheetDefinition> definitions,
		List<HistoryGeographyLandmark> spatialLandmarks,
		List<SheetGeneralKnowledge> generalKnowledge,
		List<String> limitations,
		List<SheetSource> sources,
		SheetStatus status,
		Instant generatedAt) implements Sheet {

	private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(\\d{3,4})(?!\\d)");
	private static final Pattern CENTURY_PATTERN = Pattern.compile("(?i)\\b([ivxlcdm]+)(?:e|ème)\\s+siècle\\b");
	private static final List<DateTimeFormatter> FRENCH_DATE_FORMATS = List.of(
		DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRENCH),
		DateTimeFormatter.ofPattern("d MMM uuuu", Locale.FRENCH),
		DateTimeFormatter.ofPattern("d/M/uuuu", Locale.FRENCH),
		DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRENCH));

	public HistoryGeographySheet {
		keyConcepts = List.copyOf(keyConcepts);
		chronology = chronology.stream()
			.sorted(Comparator.comparing(event -> dateOrder(event.date())))
			.toList();
		characters = List.copyOf(characters);
		definitions = List.copyOf(definitions);
		spatialLandmarks = List.copyOf(spatialLandmarks);
		generalKnowledge = List.copyOf(generalKnowledge);
		limitations = List.copyOf(limitations);
		sources = List.copyOf(sources);
	}

	private static DateOrder dateOrder(String value) {
		for (DateTimeFormatter formatter : FRENCH_DATE_FORMATS) {
			try {
				LocalDate date = LocalDate.parse(value, formatter);
				return new DateOrder(
					date.getYear(), date.getMonthValue(), date.getDayOfMonth(), value.toLowerCase(Locale.FRENCH));
			}
			catch (DateTimeParseException ignored) {
			}
		}

		Matcher yearMatcher = YEAR_PATTERN.matcher(value);
		if (yearMatcher.find()) {
			int year = Integer.parseInt(yearMatcher.group(1));
			if (value.toLowerCase(Locale.FRENCH).contains("av.")) {
				year = -year;
			}
			return new DateOrder(year, 0, 0, value.toLowerCase(Locale.FRENCH));
		}

		Matcher centuryMatcher = CENTURY_PATTERN.matcher(value);
		if (centuryMatcher.find()) {
			int century = romanValue(centuryMatcher.group(1));
			int year = (century - 1) * 100 + 50;
			if (value.toLowerCase(Locale.FRENCH).contains("av.")) {
				year = -year;
			}
			return new DateOrder(year, 0, 0, value.toLowerCase(Locale.FRENCH));
		}
		return new DateOrder(Integer.MAX_VALUE, 0, 0, value.toLowerCase(Locale.FRENCH));
	}

	private static int romanValue(String value) {
		int result = 0;
		int previous = 0;
		for (int index = value.length() - 1; index >= 0; index--) {
			int current = switch (Character.toUpperCase(value.charAt(index))) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				case 'D' -> 500;
				case 'M' -> 1000;
				default -> 0;
			};
			result += current < previous ? -current : current;
			previous = current;
		}
		return result;
	}

	private record DateOrder(int year, int month, int day, String fallback) implements Comparable<DateOrder> {

		@Override
		public int compareTo(DateOrder other) {
			int yearComparison = Integer.compare(year, other.year);
			if (yearComparison != 0) {
				return yearComparison;
			}
			int monthComparison = Integer.compare(month, other.month);
			if (monthComparison != 0) {
				return monthComparison;
			}
			int dayComparison = Integer.compare(day, other.day);
			return dayComparison != 0 ? dayComparison : fallback.compareTo(other.fallback);
		}

	}

}
