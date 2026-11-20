package dev.mickablondo.cartable.course.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class VisionImagePreprocessorTests {

	@Test
	void resizesLargePngToConfiguredPixelLimitWithoutChangingAspectRatio() throws IOException {
		byte[] original = png(40, 20);
		VisionImagePreprocessor preprocessor = new VisionImagePreprocessor(200, 1.0f);

		byte[] prepared = preprocessor.resizeIfNeeded(original, "image/png");
		BufferedImage result = ImageIO.read(new ByteArrayInputStream(prepared));

		assertThat((long) result.getWidth() * result.getHeight()).isLessThanOrEqualTo(200);
		assertThat(result.getWidth()).isEqualTo(result.getHeight() * 2);
		assertThat(prepared).isNotEqualTo(original);
		result.flush();
	}

	@Test
	void leavesImagesBelowTheConfiguredPixelLimitUntouched() throws IOException {
		byte[] original = png(10, 10);
		VisionImagePreprocessor preprocessor = new VisionImagePreprocessor(100, 1.0f);

		assertThat(preprocessor.resizeIfNeeded(original, "image/png")).isSameAs(original);
	}

	@Test
	void leavesWebpToOllamaNativePreprocessing() {
		byte[] original = { 1, 2, 3 };
		VisionImagePreprocessor preprocessor = new VisionImagePreprocessor(1, 1.0f);

		assertThat(preprocessor.resizeIfNeeded(original, "image/webp")).isSameAs(original);
	}

	private static byte[] png(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
		finally {
			image.flush();
		}
	}
}
