package dev.mickablondo.cartable.reference.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.reference.ReferenceImportException;
import dev.mickablondo.cartable.reference.ReferenceImportResult;
import dev.mickablondo.cartable.reference.ReferenceImportService;
import dev.mickablondo.cartable.reference.ReferenceIndexed;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ReferenceImportServiceImpl implements ReferenceImportService {

	private final JdbcTemplate jdbcTemplate;
	private final VectorStore vectorStore;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactionTemplate;
	private final Path referenceDirectory;
	private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();
	private final ReferenceTextSplitter textSplitter = new ReferenceTextSplitter();

	ReferenceImportServiceImpl(
			JdbcTemplate jdbcTemplate,
			VectorStore vectorStore,
			ApplicationEventPublisher events,
			TransactionTemplate transactionTemplate,
			@Value("${cartable.reference.data-directory:data/reference}") Path referenceDirectory) {
		this.jdbcTemplate = jdbcTemplate;
		this.vectorStore = vectorStore;
		this.events = events;
		this.transactionTemplate = transactionTemplate;
		this.referenceDirectory = referenceDirectory;
	}

	@Override
	public ReferenceImportResult importReferences() {
		List<Path> pdfFiles = listFiles(".pdf");
		List<Path> yamlFiles = listFiles(".yaml", ".yml");
		validateSidecarFiles(pdfFiles, yamlFiles);

		int importedDocuments = 0;
		int skippedDocuments = 0;
		int indexedChunks = 0;
		for (Path pdfFile : pdfFiles) {
			Path yamlFile = findSidecar(pdfFile, yamlFiles);
			ReferenceMetadata metadata = readMetadata(yamlFile);
			String extractedText = extractText(pdfFile);
			List<ReferenceTextSplitter.Chunk> chunks = textSplitter.split(extractedText);
			if (chunks.isEmpty()) {
				throw new ReferenceImportException(
					"Le PDF " + pdfFile.getFileName() + " ne contient aucun texte exploitable.");
			}

			String contentHash = contentHash(pdfFile, metadata);
			ImportOutcome outcome = transactionTemplate.execute(status ->
				indexDocument(pdfFile, metadata, chunks, contentHash));
			if (outcome == null) {
				throw new IllegalStateException("La transaction d'import du référentiel n'a retourné aucun résultat.");
			}
			if (outcome.skipped()) {
				skippedDocuments++;
			}
			else {
				importedDocuments++;
				indexedChunks += outcome.indexedChunks();
			}
		}
		return new ReferenceImportResult(importedDocuments, skippedDocuments, indexedChunks);
	}

	private List<Path> listFiles(String... extensions) {
		if (!Files.isDirectory(referenceDirectory)) {
			throw new ReferenceImportException(
				"Le dossier du référentiel est introuvable : " + referenceDirectory.toAbsolutePath());
		}
		try (var paths = Files.list(referenceDirectory)) {
			return paths.filter(Files::isRegularFile)
				.filter(path -> hasExtension(path, extensions))
				.sorted(Comparator.comparing(path -> path.getFileName().toString()))
				.toList();
		}
		catch (IOException exception) {
			throw new ReferenceImportException(
				"Impossible de lire le dossier du référentiel : " + referenceDirectory.toAbsolutePath(), exception);
		}
	}

	private static boolean hasExtension(Path path, String... extensions) {
		String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		for (String extension : extensions) {
			if (fileName.endsWith(extension)) {
				return true;
			}
		}
		return false;
	}

	private static void validateSidecarFiles(List<Path> pdfFiles, List<Path> yamlFiles) {
		for (Path yamlFile : yamlFiles) {
			String baseName = removeExtension(yamlFile.getFileName().toString());
			boolean hasPdf = pdfFiles.stream().anyMatch(pdf ->
				removeExtension(pdf.getFileName().toString()).equalsIgnoreCase(baseName));
			if (!hasPdf) {
				throw new ReferenceImportException(
					"Le fichier YAML " + yamlFile.getFileName() + " n'est associé à aucun PDF.");
			}
		}
	}

	private static Path findSidecar(Path pdfFile, List<Path> yamlFiles) {
		String baseName = removeExtension(pdfFile.getFileName().toString());
		List<Path> matches = yamlFiles.stream()
			.filter(path -> removeExtension(path.getFileName().toString()).equalsIgnoreCase(baseName))
			.toList();
		if (matches.isEmpty()) {
			throw new ReferenceImportException(
				"Le PDF " + pdfFile.getFileName() + " doit être accompagné d'un fichier .yaml ou .yml homonyme.");
		}
		if (matches.size() != 1) {
			throw new ReferenceImportException(
				"Plusieurs fichiers YAML correspondent au PDF " + pdfFile.getFileName() + ".");
		}
		return matches.getFirst();
	}

	private ReferenceMetadata readMetadata(Path yamlFile) {
		List<PropertySource<?>> propertySources;
		try {
			propertySources = yamlLoader.load(yamlFile.getFileName().toString(), new FileSystemResource(yamlFile));
		}
		catch (IOException exception) {
			throw new ReferenceImportException(
				"Impossible de lire le fichier YAML " + yamlFile.getFileName() + ".", exception);
		}
		if (propertySources.size() != 1) {
			throw new ReferenceImportException(
				"Le fichier YAML " + yamlFile.getFileName() + " doit contenir exactement un document.");
		}
		PropertySource<?> properties = propertySources.getFirst();
		try {
			String title = requiredText(properties, "title", yamlFile);
			String sourceUrl = requiredText(properties, "sourceUrl", yamlFile);
			validateSourceUrl(sourceUrl, yamlFile);
			LocalDate retrievedAt = requiredDate(properties, "retrievedAt", yamlFile);
			String license = requiredText(properties, "license", yamlFile);
			Subject subject = Subject.valueOf(requiredText(properties, "subject", yamlFile));
			LocalDate publicationDate = requiredDate(properties, "publicationDate", yamlFile);
			return new ReferenceMetadata(title, sourceUrl, retrievedAt, license, subject, publicationDate);
		}
		catch (IllegalArgumentException exception) {
			throw new ReferenceImportException(
				"Les métadonnées du fichier " + yamlFile.getFileName()
					+ " sont invalides : vérifiez notamment la matière et le format des dates (AAAA-MM-JJ).",
				exception);
		}
	}

	private static String requiredText(PropertySource<?> properties, String key, Path yamlFile) {
		Object value = properties.getProperty(key);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException(
				"Le champ '" + key + "' est obligatoire dans " + yamlFile.getFileName() + ".");
		}
		return text.strip();
	}

	private static LocalDate requiredDate(PropertySource<?> properties, String key, Path yamlFile) {
		Object value = properties.getProperty(key);
		if (value instanceof Date date) {
			return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
		}
		if (value instanceof String text && !text.isBlank()) {
			return LocalDate.parse(text.strip());
		}
		throw new IllegalArgumentException(
			"Le champ '" + key + "' doit être une date au format AAAA-MM-JJ dans " + yamlFile.getFileName() + ".");
	}

	private static void validateSourceUrl(String sourceUrl, Path yamlFile) {
		java.net.URI uri = java.net.URI.create(sourceUrl);
		String scheme = uri.getScheme();
		if (uri.getHost() == null || scheme == null
				|| !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw new IllegalArgumentException(
				"Le champ 'sourceUrl' doit être une URL HTTP(S) valide dans " + yamlFile.getFileName() + ".");
		}
	}

	private static String removeExtension(String fileName) {
		return fileName.substring(0, fileName.lastIndexOf('.'));
	}

	private static String extractText(Path pdfFile) {
		try {
			return new TikaDocumentReader(new FileSystemResource(pdfFile)).get().stream()
				.map(Document::getText)
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.joining(System.lineSeparator()))
				.strip();
		}
		catch (IllegalStateException exception) {
			throw new ReferenceImportException(
				"Impossible d'extraire le texte du PDF " + pdfFile.getFileName() + ".", exception);
		}
	}

	private String contentHash(Path pdfFile, ReferenceMetadata metadata) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(Files.readAllBytes(pdfFile));
			digest.update(pdfFile.getFileName().toString().getBytes(StandardCharsets.UTF_8));
			digest.update(metadata.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (IOException exception) {
			throw new ReferenceImportException(
				"Impossible de lire le PDF " + pdfFile.getFileName() + ".", exception);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("L'algorithme SHA-256 est indisponible.", exception);
		}
	}

	private ImportOutcome indexDocument(
			Path pdfFile, ReferenceMetadata metadata, List<ReferenceTextSplitter.Chunk> chunks, String contentHash) {
		UUID referenceId = UUID.nameUUIDFromBytes(metadata.sourceUrl().getBytes(StandardCharsets.UTF_8));
		ExistingReference existing = findExisting(metadata.sourceUrl());
		if (existing != null && existing.contentHash().equals(contentHash)) {
			return new ImportOutcome(true, 0);
		}

		List<Document> documents = new ArrayList<>(chunks.size());
		for (int index = 0; index < chunks.size(); index++) {
			String documentId = UUID.nameUUIDFromBytes(
				(metadata.sourceUrl() + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
			documents.add(Document.builder()
				.id(documentId)
				.text(chunks.get(index).text())
				.metadata(Map.of(
					"origin", "OFFICIAL_REFERENCE",
					"title", metadata.title(),
					"sourceUrl", metadata.sourceUrl(),
					"retrievedAt", metadata.retrievedAt().toString(),
					"license", metadata.license(),
					"subject", metadata.subject().name(),
					"publicationDate", metadata.publicationDate().toString(),
					"sourceFile", pdfFile.getFileName().toString(),
					"chapter", chunks.get(index).chapter()))
				.build());
		}

		vectorStore.add(documents);
		if (existing != null && existing.indexedChunks() > documents.size()) {
			List<String> staleDocumentIds = new ArrayList<>();
			for (int index = documents.size(); index < existing.indexedChunks(); index++) {
				staleDocumentIds.add(UUID.nameUUIDFromBytes(
					(metadata.sourceUrl() + "#" + index).getBytes(StandardCharsets.UTF_8)).toString());
			}
			vectorStore.delete(staleDocumentIds);
		}

		jdbcTemplate.update("""
			INSERT INTO reference_document
				(id, source_url, source_file, title, retrieved_at, license, subject, publication_date,
				 content_hash, indexed_chunks, status)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'INDEXED')
			ON CONFLICT (source_url) DO UPDATE SET
				source_file = EXCLUDED.source_file,
				title = EXCLUDED.title,
				retrieved_at = EXCLUDED.retrieved_at,
				license = EXCLUDED.license,
				subject = EXCLUDED.subject,
				publication_date = EXCLUDED.publication_date,
				content_hash = EXCLUDED.content_hash,
				indexed_chunks = EXCLUDED.indexed_chunks,
				status = EXCLUDED.status
			""",
			referenceId,
			metadata.sourceUrl(),
			pdfFile.getFileName().toString(),
			metadata.title(),
			metadata.retrievedAt(),
			metadata.license(),
			metadata.subject().name(),
			metadata.publicationDate(),
			contentHash,
			documents.size());

		events.publishEvent(new ReferenceIndexed(
			referenceId, metadata.title(), metadata.sourceUrl(), documents.size()));
		return new ImportOutcome(false, documents.size());
	}

	private ExistingReference findExisting(String sourceUrl) {
		List<ExistingReference> references = jdbcTemplate.query("""
			SELECT content_hash, indexed_chunks
			FROM reference_document
			WHERE source_url = ?
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new ExistingReference(
				resultSet.getString("content_hash"), resultSet.getInt("indexed_chunks")),
			sourceUrl);
		return references.isEmpty() ? null : references.getFirst();
	}

	private record ReferenceMetadata(
			String title,
			String sourceUrl,
			LocalDate retrievedAt,
			String license,
			Subject subject,
			LocalDate publicationDate) {
	}

	private record ExistingReference(String contentHash, int indexedChunks) {
	}

	private record ImportOutcome(boolean skipped, int indexedChunks) {
	}

}
