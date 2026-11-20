package dev.mickablondo.cartable.course.internal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Limites appliquées aux fichiers importés avant leur lecture ou leur stockage.
 */
@ConfigurationProperties("cartable.course.upload")
public record CourseUploadProperties(
		int maxImagesPerCourse,
		DataSize maxImageSize,
		DataSize maxScannedPdfSize,
		DataSize maxFileSize,
		DataSize maxRequestSize) {

	private static final long MULTIPART_OVERHEAD_BYTES = DataSize.ofMegabytes(1).toBytes();
	private static final int MINIMUM_FULL_SIZE_IMAGES_PER_REQUEST = 3;

	public CourseUploadProperties {
		if (maxImagesPerCourse < 1
				|| maxImageSize.toBytes() < 1
				|| maxScannedPdfSize.toBytes() < 1
				|| maxFileSize.toBytes() < 1
				|| maxRequestSize.toBytes() < 1) {
			throw new IllegalArgumentException("Les limites d'import doivent être strictement positives.");
		}
		if (maxFileSize.compareTo(maxImageSize) < 0 || maxFileSize.compareTo(maxScannedPdfSize) < 0) {
			throw new IllegalArgumentException(
				"cartable.course.upload.max-file-size doit couvrir les limites des images et des PDF.");
		}
		long minimumRequestSize = Math.max(
			Math.max(
				Math.multiplyExact(maxImageSize.toBytes(), MINIMUM_FULL_SIZE_IMAGES_PER_REQUEST),
				maxScannedPdfSize.toBytes()),
			maxFileSize.toBytes());
		minimumRequestSize = Math.addExact(minimumRequestSize, MULTIPART_OVERHEAD_BYTES);
		if (maxRequestSize.toBytes() < minimumRequestSize) {
			throw new IllegalArgumentException(
				"cartable.course.upload.max-request-size doit permettre au moins trois images à leur taille maximale et un fichier individuel maximal.");
		}
	}

	long maxSizeFor(String extension) {
		if (isImage(extension)) {
			return maxImageSize.toBytes();
		}
		if (extension.equals(".pdf")) {
			return maxScannedPdfSize.toBytes();
		}
		return maxFileSize.toBytes();
	}

	private static boolean isImage(String extension) {
		return extension.equals(".jpg") || extension.equals(".jpeg")
			|| extension.equals(".png") || extension.equals(".webp");
	}
}
