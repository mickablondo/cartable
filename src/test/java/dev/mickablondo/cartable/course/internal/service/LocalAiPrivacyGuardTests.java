package dev.mickablondo.cartable.course.internal.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;

class LocalAiPrivacyGuardTests {

	@Test
	void rejectsACloudChatModel() {
		assertThrows(IllegalStateException.class, () -> new LocalAiPrivacyGuard(
			List.of(mock(ChatModel.class)), List.of(mock(OllamaEmbeddingModel.class))));
	}

	@Test
	void rejectsACloudEmbeddingModel() {
		assertThrows(IllegalStateException.class, () -> new LocalAiPrivacyGuard(
			List.of(mock(OllamaChatModel.class)), List.of(mock(EmbeddingModel.class))));
	}

}
