package dev.mickablondo.cartable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CartableApplicationTests {

	private static final int EXPECTED_EMBEDDING_DIMENSIONS = 1024;
	private static final Pattern VECTOR_DIMENSIONS_PATTERN =
		Pattern.compile("embedding\\s+vector\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);

	@Value("${spring.ai.vectorstore.pgvector.dimensions}")
	private int configuredEmbeddingDimensions;

	@Test
	void contextLoads() {
	}

	@Test
	void embeddingDimensionsMatchConfigurationMigrationAndReference() throws IOException {
		var migration = new ClassPathResource("db/migration/V2__create_course_and_vector_store.sql");
		String migrationSql;
		try (var inputStream = migration.getInputStream()) {
			migrationSql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}

		Matcher matcher = VECTOR_DIMENSIONS_PATTERN.matcher(migrationSql);
		assertTrue(matcher.find(), "La migration doit déclarer la dimension de la colonne vectorielle.");
		int migrationDimensions = Integer.parseInt(matcher.group(1));
		assertFalse(matcher.find(), "La migration doit déclarer une seule dimension vectorielle.");

		assertEquals(EXPECTED_EMBEDDING_DIMENSIONS, configuredEmbeddingDimensions,
			"La propriété Spring AI doit correspondre à la dimension vérifiée du modèle.");
		assertEquals(EXPECTED_EMBEDDING_DIMENSIONS, migrationDimensions,
			"La migration Flyway doit correspondre à la dimension vérifiée du modèle.");
	}

}
