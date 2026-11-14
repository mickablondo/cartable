package dev.mickablondo.cartable.course.internal;

import dev.mickablondo.cartable.course.CourseImported;
import dev.mickablondo.cartable.course.CourseIndexed;
import dev.mickablondo.cartable.course.Subject;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Composant écoutant les événements d'importation de cours et indexant le texte extrait dans un magasin vectoriel.
 */
@Component
class CourseIndexingListener {

	private static final String LEVEL = "3ème";

	private final JdbcTemplate jdbcTemplate;
	private final VectorStore vectorStore;
	private final ApplicationEventPublisher events;
	private final CourseTextSplitter textSplitter = new CourseTextSplitter();

	CourseIndexingListener(JdbcTemplate jdbcTemplate, VectorStore vectorStore, ApplicationEventPublisher events) {
		this.jdbcTemplate = jdbcTemplate;
		this.vectorStore = vectorStore;
		this.events = events;
	}

	@ApplicationModuleListener
	void onCourseImported(CourseImported event) {
		IndexedCourse course = jdbcTemplate.queryForObject("""
			SELECT file_name, subject, extracted_text
			FROM course
			WHERE id = ?
			""",
			(resultSet, rowNumber) -> new IndexedCourse(
				resultSet.getString("file_name"),
				Subject.valueOf(resultSet.getString("subject")),
				resultSet.getString("extracted_text")),
			event.courseId());

		List<CourseTextSplitter.Chunk> chunks = textSplitter.split(course.extractedText());
		if (chunks.isEmpty()) {
			throw new IllegalStateException("Le cours importé ne contient aucun passage à indexer.");
		}

		List<Document> documents = chunks.stream()
			.map(chunk -> Document.builder()
				.text(chunk.text())
				.metadata(Map.of(
					"origin", "COURSE",
					"subject", course.subject().name(),
					"level", LEVEL,
					"chapter", chunk.chapter(),
					"sourceFile", course.fileName()))
				.build())
			.toList();

		vectorStore.add(documents);
		int updatedRows = jdbcTemplate.update(
			"UPDATE course SET status = 'INDEXED' WHERE id = ? AND status = 'IMPORTED'", event.courseId());
		if (updatedRows != 1) {
			throw new IllegalStateException("Le statut du cours a changé avant la fin de son indexation.");
		}
		events.publishEvent(new CourseIndexed(event.courseId(), documents.size()));
	}

	/**
	 * Représente un cours indexé avec son nom de fichier, sa matière et le texte extrait.
	 * @param fileName nom du fichier du cours
	 * @param subject matière du cours
	 * @param extractedText texte extrait du cours
	 */
	private record IndexedCourse(String fileName, Subject subject, String extractedText) {
	}
}
