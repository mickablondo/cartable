package dev.mickablondo.cartable.course.internal.service;

import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cartable.ai.require-local-models", havingValue = "true", matchIfMissing = true)
final class LocalAiPrivacyGuard {

	LocalAiPrivacyGuard(List<ChatModel> chatModels, List<EmbeddingModel> embeddingModels) {
		requireLocalModels(chatModels, OllamaChatModel.class, "conversation");
		requireLocalModels(embeddingModels, OllamaEmbeddingModel.class, "embedding");
	}

	private static void requireLocalModels(List<?> models, Class<?> localModelType, String role) {
		if (models.isEmpty() || models.stream().anyMatch(model -> !localModelType.isInstance(model))) {
			throw new IllegalStateException(
				"Les modèles de %s doivent tous être locaux (Ollama) pour protéger les données des cours."
					.formatted(role));
		}
	}

}
