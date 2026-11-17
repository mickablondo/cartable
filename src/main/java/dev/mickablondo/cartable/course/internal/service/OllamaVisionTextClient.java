package dev.mickablondo.cartable.course.internal.service;

import java.util.List;
import java.util.Locale;

import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.ResourceAccessException;

@Component
class OllamaVisionTextClient implements VisionTextClient {

	private final OllamaChatModel chatModel;
	private final String modelName;

	OllamaVisionTextClient(
			OllamaChatModel chatModel,
			@Value("${cartable.vision.model:}") String modelName) {
		this.chatModel = chatModel;
		this.modelName = modelName;
	}

	@Override
	public synchronized String transcribe(String prompt, byte[] image, String mediaType) {
		if (modelName.isBlank()) {
			throw new VisionTextException("Le modèle de vision n'est pas configuré. Définissez VISION_MODEL.");
		}

		UserMessage message = UserMessage.builder()
			.text(prompt)
			.media(new Media(MimeTypeUtils.parseMimeType(mediaType), new ByteArrayResource(image)))
			.build();
		try {
			String text = chatModel.call(new Prompt(List.of(message),
				OllamaChatOptions.builder().model(modelName).disableThinking().build()))
				.getResult().getOutput().getText();
			if (text == null || text.isBlank()) {
				throw new VisionTextException("L'image est illisible ou aucun texte n'a été reconnu.");
			}
			return text.strip();
		}
		catch (NonTransientAiException exception) {
			String details = exception.getMessage() == null ? "" : exception.getMessage();
			if (details.toLowerCase(Locale.ROOT).contains("model")
					&& details.toLowerCase(Locale.ROOT).contains("not found")) {
				throw new VisionTextException(
					"Le modèle de vision \"%s\" n'est pas disponible dans Ollama. "
						.formatted(modelName) + "Installez-le ou choisissez un modèle disponible dans VISION_MODEL.",
					exception);
			}
			throw new VisionTextException("Ollama a refusé la lecture de cette image : " + details, exception);
		}
		catch (ResourceAccessException exception) {
			throw new VisionTextException(
				"Impossible de joindre Ollama pour lire cette image. Vérifiez que le service est démarré.", exception);
		}
	}

}
