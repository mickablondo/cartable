package dev.mickablondo.cartable.course.internal;

import java.util.List;

import dev.mickablondo.cartable.course.CoursePassage;
import dev.mickablondo.cartable.course.CourseSearchService;
import dev.mickablondo.cartable.course.Subject;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
class CourseSearchServiceImpl implements CourseSearchService {

	private final VectorStore vectorStore;

	CourseSearchServiceImpl(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Override
	public List<CoursePassage> search(String query, Subject subject) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("La requête de recherche est obligatoire.");
		}
		if (subject == null) {
			throw new IllegalArgumentException("La matière est obligatoire.");
		}

		String filter = "origin == 'COURSE' && subject == '%s'".formatted(subject.name());
		return vectorStore.similaritySearch(SearchRequest.builder()
				.query(query)
				.filterExpression(filter)
				.build())
			.stream()
			.map(document -> new CoursePassage(
				requiredText(document),
				requiredMetadata(document, "sourceFile"),
				metadataOrDefault(document, "chapter", "Sans chapitre détecté")))
			.toList();
	}

	private static String requiredText(Document document) {
		String text = document.getText();
		if (text == null) {
			throw new IllegalStateException("Un passage de cours indexé ne contient aucun texte.");
		}
		return text;
	}

	private static String requiredMetadata(Document document, String key) {
		Object value = document.getMetadata().get(key);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalStateException("La métadonnée '" + key + "' est absente d'un passage de cours.");
		}
		return text;
	}

	private static String metadataOrDefault(Document document, String key, String defaultValue) {
		Object value = document.getMetadata().get(key);
		return value instanceof String text && !text.isBlank() ? text : defaultValue;
	}

}
