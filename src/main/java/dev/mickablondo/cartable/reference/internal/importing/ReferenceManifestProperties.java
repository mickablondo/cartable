package dev.mickablondo.cartable.reference.internal.importing;

import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import dev.mickablondo.cartable.course.Subject;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties("cartable.reference")
public record ReferenceManifestProperties(
		@DefaultValue("data/reference") Path dataDirectory,
		@DefaultValue List<ReferenceManifestEntry> documents) {

	public ReferenceManifestProperties {
		documents = List.copyOf(documents);
	}

	public record ReferenceManifestEntry(
			String id,
			String title,
			Subject subject,
			URI pdfUrl,
			URI pageUrl,
			LocalDate publicationDate,
			String license,
			@Name("class") String schoolClass,
			List<String> schoolYears) {

		public ReferenceManifestEntry {
			schoolYears = List.copyOf(schoolYears);
		}

	}

}
