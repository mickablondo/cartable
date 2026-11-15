package dev.mickablondo.cartable.reference.internal;

import java.time.LocalDate;
import java.util.List;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.reference.ReferencePassage;
import dev.mickablondo.cartable.reference.ReferenceSearchService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
class ReferenceSearchServiceImpl implements ReferenceSearchService {

	private final VectorStore vectorStore;

	ReferenceSearchServiceImpl(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Override
	public List<ReferencePassage> search(String query, Subject subject) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("La requête de recherche est obligatoire.");
		}
		if (subject == null) {
			throw new IllegalArgumentException("La matière est obligatoire.");
		}

		String filter = "origin == 'OFFICIAL_REFERENCE' && subject == '%s'".formatted(subject.name());
		return vectorStore.similaritySearch(SearchRequest.builder()
				.query(query)
				.filterExpression(filter)
				.build())
			.stream()
			.map(ReferenceSearchServiceImpl::toPassage)
			.toList();
	}

	private static ReferencePassage toPassage(Document document) {
		return new ReferencePassage(
			requiredText(document),
			requiredMetadata(document, "title"),
			requiredMetadata(document, "sourceUrl"),
			requiredMetadata(document, "sourceFile"),
			metadataOrDefault(document, "chapter", "Sans chapitre détecté"),
			LocalDate.parse(requiredMetadata(document, "publicationDate")));
	}

	private static String requiredText(Document document) {
		String text = document.getText();
		if (text == null) {
			throw new IllegalStateException("Un passage du référentiel indexé ne contient aucun texte.");
		}
		return text;
	}

	private static String requiredMetadata(Document document, String key) {
		Object value = document.getMetadata().get(key);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalStateException("La métadonnée '" + key + "' est absente d'un passage du référentiel.");
		}
		return text;
	}

	private static String metadataOrDefault(Document document, String key, String defaultValue) {
		Object value = document.getMetadata().get(key);
		return value instanceof String text && !text.isBlank() ? text : defaultValue;
	}

}
