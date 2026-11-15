package dev.mickablondo.cartable.reference.internal.importing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;
import dev.mickablondo.cartable.reference.ReferenceImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferencePdfDownloaderTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void rejectsResponsesLargerThanTwentyFiveMegabytes() throws IOException {
		byte[] oversizedBody = new byte[25 * 1024 * 1024 + 1];
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/large.pdf", exchange -> {
			try {
				exchange.sendResponseHeaders(200, oversizedBody.length);
				try (var output = exchange.getResponseBody()) {
					output.write(oversizedBody);
				}
			}
			catch (IOException ignored) {
			}
		});
		server.start();
		Path target = temporaryDirectory.resolve("large.pdf");
		try {
			ReferenceImportException exception = assertThrows(ReferenceImportException.class,
				() -> new ReferencePdfDownloader().download(
					java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/large.pdf"),
					target));
			assertTrue(exception.getMessage().contains("25 Mo"));
			assertFalse(Files.exists(target));
		}
		finally {
			server.stop(0);
		}
	}

}
