package dev.mickablondo.cartable.reference.internal.importing;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import dev.mickablondo.cartable.reference.ReferenceImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReferencePdfDownloader {

	private static final Logger logger = LoggerFactory.getLogger(ReferencePdfDownloader.class);
	private static final int CONNECT_TIMEOUT_MILLIS = (int) Duration.ofSeconds(5).toMillis();
	private static final int READ_TIMEOUT_MILLIS = (int) Duration.ofSeconds(30).toMillis();
	private static final long MAX_DOWNLOAD_BYTES = 25L * 1024 * 1024;
	private static final byte[] PDF_HEADER = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

	public Path download(URI uri, Path target) {
		HttpURLConnection connection = null;
		Path temporaryFile = null;
		try {
			connection = (HttpURLConnection) uri.toURL().openConnection();
			connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
			connection.setReadTimeout(READ_TIMEOUT_MILLIS);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("GET");

			int status = connection.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				throw new ReferenceImportException("Le serveur a répondu HTTP " + status + ".");
			}
			long contentLength = connection.getContentLengthLong();
			if (contentLength > MAX_DOWNLOAD_BYTES) {
				throw new ReferenceImportException("Le PDF dépasse la taille maximale de 25 Mo.");
			}

			Files.createDirectories(target.getParent());
			temporaryFile = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".download");
			try (InputStream input = connection.getInputStream();
				 var output = Files.newOutputStream(temporaryFile)) {
				byte[] buffer = new byte[8192];
				long totalBytes = 0;
				int bytesRead;
				while ((bytesRead = input.read(buffer)) != -1) {
					totalBytes += bytesRead;
					if (totalBytes > MAX_DOWNLOAD_BYTES) {
						throw new ReferenceImportException("Le PDF dépasse la taille maximale de 25 Mo.");
					}
					output.write(buffer, 0, bytesRead);
				}
			}

			if (!isPdf(temporaryFile)) {
				throw new ReferenceImportException("La réponse téléchargée n'est pas un fichier PDF valide.");
			}
			moveIntoPlace(temporaryFile, target);
			return target;
		}
		catch (IOException exception) {
			throw new ReferenceImportException("Impossible de télécharger le PDF : " + exception.getMessage(), exception);
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
			if (temporaryFile != null) {
				try {
					Files.deleteIfExists(temporaryFile);
				}
				catch (IOException exception) {
					logger.error("Impossible de supprimer le fichier temporaire de téléchargement {}.",
						temporaryFile, exception);
				}
			}
		}
	}

	public static boolean isPdf(Path file) throws IOException {
		try (InputStream input = Files.newInputStream(file)) {
			byte[] header = input.readNBytes(PDF_HEADER.length);
			return java.util.Arrays.equals(header, PDF_HEADER);
		}
	}

	private static void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
		try {
			Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

}
