package dev.mickablondo.cartable.course.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

class OllamaVisionTextClientTests {

	@Test
	void sendsImageWithConfiguredModelAndDisabledThinking() {
		OllamaChatModel chatModel = mock(OllamaChatModel.class);
		ChatResponse response = mock(ChatResponse.class);
		Generation generation = mock(Generation.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(response);
		when(response.getResult()).thenReturn(generation);
		when(generation.getOutput()).thenReturn(new AssistantMessage("Transcription exacte."));
		OllamaVisionTextClient client = new OllamaVisionTextClient(
			chatModel, "vision-test", "http://localhost:11434");

		String text = client.transcribe("Consigne de test", new byte[] { 1, 2, 3 }, "image/png");

		assertThat(text).isEqualTo("Transcription exacte.");
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		verify(chatModel).call(promptCaptor.capture());
		assertThat(promptCaptor.getValue().getOptions())
			.isEqualTo(OllamaChatOptions.builder().model("vision-test").disableThinking().build());
		assertThat(promptCaptor.getValue().getInstructions()).hasSize(1);
	}

	@Test
	void reportsMissingVisionModelWithoutCallingOllama() {
		OllamaChatModel chatModel = mock(OllamaChatModel.class);
		OllamaVisionTextClient client = new OllamaVisionTextClient(
			chatModel, "", "http://127.0.0.1:11434");

		assertThatThrownBy(() -> client.transcribe("Consigne", new byte[] { 1 }, "image/png"))
			.isInstanceOf(VisionTextException.class)
			.hasMessageContaining("VISION_MODEL");
		verify(chatModel, never()).call(any(Prompt.class));
	}

	@Test
	void reportsAnEmptyModelResponseAsUnreadable() {
		OllamaChatModel chatModel = mock(OllamaChatModel.class);
		ChatResponse response = mock(ChatResponse.class);
		Generation generation = mock(Generation.class);
		when(chatModel.call(any(Prompt.class))).thenReturn(response);
		when(response.getResult()).thenReturn(generation);
		when(generation.getOutput()).thenReturn(new AssistantMessage("  "));
		OllamaVisionTextClient client = new OllamaVisionTextClient(
			chatModel, "vision-test", "http://localhost:11434");

		assertThatThrownBy(() -> client.transcribe("Consigne", new byte[] { 1 }, "image/png"))
			.isInstanceOf(VisionTextException.class)
			.hasMessageContaining("illisible");
	}

	@Test
	void refusesRemoteOllamaEndpointBeforeAnyImageCanBeSent() {
		OllamaChatModel chatModel = mock(OllamaChatModel.class);

		assertThatThrownBy(() ->
			new OllamaVisionTextClient(chatModel, "vision-test", "https://cloud.example.test"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("localhost");

		verify(chatModel, never()).call(any(Prompt.class));
	}

}
