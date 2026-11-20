package dev.mickablondo.cartable.course.internal.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

final class VisionImagePreprocessor {

	private static final String JPEG_MEDIA_TYPE = "image/jpeg";
	private static final String PNG_MEDIA_TYPE = "image/png";
	private static final String WEBP_MEDIA_TYPE = "image/webp";

	private final long maxImagePixels;
	private final float jpegQuality;

	VisionImagePreprocessor(long maxImagePixels, float jpegQuality) {
		if (maxImagePixels < 1 || jpegQuality < 0 || jpegQuality > 1) {
			throw new IllegalArgumentException("Les paramètres de prétraitement des images sont invalides.");
		}
		this.maxImagePixels = maxImagePixels;
		this.jpegQuality = jpegQuality;
	}

	byte[] resizeIfNeeded(byte[] original, String mediaType) {
		if (mediaType.equals(WEBP_MEDIA_TYPE)) {
			return original;
		}
		if (!mediaType.equals(JPEG_MEDIA_TYPE) && !mediaType.equals(PNG_MEDIA_TYPE)) {
			throw new VisionTextException("Le type d'image ne peut pas être préparé pour la lecture.");
		}

		BufferedImage source;
		try {
			source = ImageIO.read(new ByteArrayInputStream(original));
		}
		catch (IOException exception) {
			throw new VisionTextException("Impossible de décoder l'image avant sa lecture.", exception);
		}
		if (source == null) {
			throw new VisionTextException("L'image est illisible ou son format n'est pas reconnu.");
		}

		long sourcePixels = (long) source.getWidth() * source.getHeight();
		if (sourcePixels <= maxImagePixels) {
			source.flush();
			return original;
		}

		double scale = Math.sqrt((double) maxImagePixels / sourcePixels);
		int width = Math.max(1, (int) Math.floor(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.floor(source.getHeight() * scale));
		BufferedImage resized = new BufferedImage(
			width,
			height,
			mediaType.equals(PNG_MEDIA_TYPE) && source.getColorModel().hasAlpha()
				? BufferedImage.TYPE_INT_ARGB
				: BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = resized.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
			source.flush();
		}

		try {
			return encode(resized, mediaType);
		}
		catch (IOException exception) {
			throw new VisionTextException("Impossible de préparer l'image redimensionnée pour sa lecture.", exception);
		}
		finally {
			resized.flush();
		}
	}

	private byte[] encode(BufferedImage image, String mediaType) throws IOException {
		if (mediaType.equals(PNG_MEDIA_TYPE)) {
			try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
				if (!ImageIO.write(image, "png", output)) {
					throw new IOException("Aucun encodeur PNG n'est disponible.");
				}
				return output.toByteArray();
			}
		}

		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			throw new IOException("Aucun encodeur JPEG n'est disponible.");
		}
		ImageWriter writer = writers.next();
		try (ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
			writer.setOutput(imageOutput);
			ImageWriteParam parameters = writer.getDefaultWriteParam();
			parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parameters.setCompressionQuality(jpegQuality);
			writer.write(null, new IIOImage(image, null, null), parameters);
			imageOutput.flush();
			return output.toByteArray();
		}
		finally {
			writer.dispose();
		}
	}
}
