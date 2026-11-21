package dev.mickablondo.cartable;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class SheetSchemaMigrationTests {

	private static final String SHEET_ID = UUID.fromString("6a7dce15-8fb2-4c64-b9f0-d20c470e32d4").toString();

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
		DockerImageName.parse("pgvector/pgvector:pg16"));

	@Test
	void migratesExistingGenericSheetDataWithoutRemovingLegacyColumns() throws SQLException {
		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.target(MigrationVersion.fromVersion("11"))
			.load()
			.migrate();

		try (var connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
			 var statement = connection.createStatement()) {
			statement.executeUpdate("""
				INSERT INTO sheet
					(id, subject, theme, key_concepts, definitions, landmarks, general_knowledge, limitations,
					 sources, status, generated_at)
				VALUES ('%s', 'MATHEMATICS', 'Géométrie', '[]'::jsonb,
					'[{"term":"Triangle","definition":"Polygone à trois côtés.","sourceIds":["C1"]}]'::jsonb,
					'[{"text":"Un triangle a trois côtés.","sourceIds":["C1"]}]'::jsonb,
					'[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'TO_VERIFY', now())
				""".formatted(SHEET_ID));
		}

		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.load()
			.migrate();

		try (var connection = DriverManager.getConnection(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
			 var statement = connection.createStatement();
			 var result = statement.executeQuery("""
				SELECT sheet_type, schema_version,
				       variant_data -> 'definitions' -> 0 ->> 'term' AS migrated_term,
				       definitions -> 0 ->> 'term' AS legacy_term,
				       landmarks -> 0 ->> 'text' AS legacy_landmark
				FROM sheet
				WHERE id = '%s'
				""".formatted(SHEET_ID))) {
			result.next();
			assertEquals("GENERIC", result.getString("sheet_type"));
			assertEquals(1, result.getInt("schema_version"));
			assertEquals("Triangle", result.getString("migrated_term"));
			assertEquals("Triangle", result.getString("legacy_term"));
			assertEquals("Un triangle a trois côtés.", result.getString("legacy_landmark"));
		}
	}

}
