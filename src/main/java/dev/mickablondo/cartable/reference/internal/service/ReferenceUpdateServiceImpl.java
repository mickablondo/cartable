package dev.mickablondo.cartable.reference.internal.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mickablondo.cartable.reference.ReferenceImportException;
import dev.mickablondo.cartable.reference.ReferenceIndexed;
import dev.mickablondo.cartable.reference.ReferenceUpdateIssue;
import dev.mickablondo.cartable.reference.ReferenceUpdateReport;
import dev.mickablondo.cartable.reference.ReferenceUpdateService;
import dev.mickablondo.cartable.reference.internal.importing.ReferenceManifestProperties;
import dev.mickablondo.cartable.reference.internal.importing.ReferenceManifestProperties.ReferenceManifestEntry;
import dev.mickablondo.cartable.reference.internal.importing.ReferencePdfDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ReferenceUpdateServiceImpl implements ReferenceUpdateService {

	private static final Logger logger = LoggerFactory.getLogger(ReferenceUpdateServiceImpl.class);
	private static final String MANUAL_PDF_INSTRUCTION =
		" Vous pouvez déposer le PDF dans data/reference/<id>.pdf ; il sera utilisé lors de la prochaine mise à jour.";

	private final JdbcTemplate jdbcTemplate;
	private final VectorStore vectorStore;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactionTemplate;
	private final ReferenceManifestProperties properties;
	private final ReferencePdfDownloader downloader;
	private final ReferenceTextSplitter textSplitter = new ReferenceTextSplitter();

	ReferenceUpdateServiceImpl(
			JdbcTemplate jdbcTemplate,
			VectorStore vectorStore,
			ApplicationEventPublisher events,
			TransactionTemplate transactionTemplate,
			ReferenceManifestProperties properties,
			ReferencePdfDownloader downloader) {
		this.jdbcTemplate = jdbcTemplate;
		this.vectorStore = vectorStore;
		this.events = events;
		this.transactionTemplate = transactionTemplate;
		this.properties = properties;
		this.downloader = downloader;
	}

	@Override
	public ReferenceUpdateReport updateReference() {
		Path referenceDirectory = properties.dataDirectory().toAbsolutePath().normalize();
		try {
			Files.createDirectories(referenceDirectory);
		}
		catch (IOException exception) {
			throw new ReferenceImportException(
				"Impossible de créer le dossier du référentiel : " + referenceDirectory, exception);
		}

		List<ReferenceUpdateIssue> issues = new ArrayList<>();
		Set<String> documentIds = new HashSet<>();
		Set<String> pageUrls = new HashSet<>();
		int updatedDocuments = 0;
		int unchangedDocuments = 0;
		int indexedChunks = 0;

		for (ReferenceManifestEntry entry : properties.documents()) {
			try {
				validateEntry(entry, documentIds, pageUrls);
				Path pdfFile = referenceDirectory.resolve(entry.id() + ".pdf");
				String downloadFailure = null;
				boolean localPdfUsed = false;
				try {
					downloader.download(entry.pdfUrl(), pdfFile);
				}
				catch (ReferenceImportException exception) {
					downloadFailure = exception.getMessage();
					if (isUsableLocalPdf(pdfFile)) {
						localPdfUsed = true;
					}
					else {
						issues.add(issue(entry, downloadFailure, false));
						logger.warn("Échec du téléchargement du document de référence {} depuis {}: {}",
							entry.id(), entry.pdfUrl(), downloadFailure);
						continue;
					}
				}

				ReferenceMetadata metadata = new ReferenceMetadata(
					entry, LocalDate.now(), pdfFile.getFileName().toString());
				String extractedText = extractText(pdfFile);
				List<ReferenceTextSplitter.Chunk> chunks = textSplitter.split(extractedText);
				if (chunks.isEmpty()) {
					throw new ReferenceImportException(
						"Le PDF " + pdfFile.getFileName() + " ne contient aucun texte exploitable.");
				}

				String contentHash = contentHash(pdfFile);
				ImportOutcome outcome = transactionTemplate.execute(status ->
					indexDocument(metadata, chunks, contentHash));
				if (outcome == null) {
					throw new IllegalStateException("La transaction d'import du référentiel n'a retourné aucun résultat.");
				}
				if (outcome.skipped()) {
					unchangedDocuments++;
				}
				else {
					updatedDocuments++;
					indexedChunks += outcome.indexedChunks();
				}
				if (downloadFailure != null) {
					issues.add(issue(entry, downloadFailure, localPdfUsed));
				}
			}
			catch (RuntimeException exception) {
				String reason = exception.getMessage() == null ? exception.getClass().getSimpleName()
					: exception.getMessage();
				issues.add(issue(entry, reason, false));
				logger.warn("Impossible de mettre à jour le document de référence {} depuis {}: {}",
					entry.id(), entry.pdfUrl(), reason, exception);
			}
		}
		return new ReferenceUpdateReport(updatedDocuments, unchangedDocuments, indexedChunks, issues);
	}

	private static void validateEntry(
			ReferenceManifestEntry entry, Set<String> documentIds, Set<String> pageUrls) {
		if (entry.id() == null || !entry.id().matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")
				|| entry.id().equals(".") || entry.id().equals("..")) {
			throw new ReferenceImportException("L'identifiant du document est invalide.");
		}
		if (!documentIds.add(entry.id())) {
			throw new ReferenceImportException("L'identifiant " + entry.id() + " apparaît plusieurs fois dans le manifeste.");
		}
		if (entry.title() == null || entry.title().isBlank() || entry.subject() == null
				|| entry.pdfUrl() == null || entry.pageUrl() == null || entry.publicationDate() == null
				|| entry.license() == null || entry.license().isBlank()
				|| entry.schoolClass() == null || entry.schoolClass().isBlank()
				|| entry.schoolYears().isEmpty()) {
			throw new ReferenceImportException("Une ou plusieurs métadonnées obligatoires sont absentes.");
		}
		validateHttpUrl(entry.pdfUrl().toString(), "pdfUrl");
		validateHttpUrl(entry.pageUrl().toString(), "pageUrl");
		if (!pageUrls.add(entry.pageUrl().toString())) {
			throw new ReferenceImportException("La page officielle apparaît plusieurs fois dans le manifeste.");
		}
	}

	private static void validateHttpUrl(String value, String field) {
		var uri = java.net.URI.create(value);
		String scheme = uri.getScheme();
		if (uri.getHost() == null || scheme == null
				|| !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw new ReferenceImportException("Le champ '" + field + "' doit être une URL HTTP(S) valide.");
		}
	}

	private static boolean isUsableLocalPdf(Path file) {
		try {
			return Files.isRegularFile(file) && ReferencePdfDownloader.isPdf(file);
		}
		catch (IOException exception) {
			throw new ReferenceImportException("Impossible de vérifier le PDF local " + file.getFileName() + ".", exception);
		}
	}

	private static ReferenceUpdateIssue issue(ReferenceManifestEntry entry, String reason, boolean localPdfUsed) {
		String detail = reason + MANUAL_PDF_INSTRUCTION.replace("<id>", entry.id());
		return new ReferenceUpdateIssue(entry.id(), String.valueOf(entry.pdfUrl()), detail, localPdfUsed);
	}

	private static String extractText(Path pdfFile) {
		try {
			return new TikaDocumentReader(new org.springframework.core.io.FileSystemResource(pdfFile)).get().stream()
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

	private static String contentHash(Path pdfFile) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(pdfFile)));
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
			ReferenceMetadata metadata, List<ReferenceTextSplitter.Chunk> chunks, String contentHash) {
		String sourceUrl = metadata.entry().pageUrl().toString();
		UUID referenceId = UUID.nameUUIDFromBytes(sourceUrl.getBytes(StandardCharsets.UTF_8));
		ExistingReference existing = findExisting(sourceUrl);
		if (existing != null && existing.contentHash().equals(contentHash)) {
			jdbcTemplate.update("UPDATE reference_document SET retrieved_at = ? WHERE source_url = ?",
				metadata.retrievedAt(), sourceUrl);
			return new ImportOutcome(true, 0);
		}

		List<Document> documents = new ArrayList<>(chunks.size());
		for (int index = 0; index < chunks.size(); index++) {
			String documentId = UUID.nameUUIDFromBytes(
				(sourceUrl + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
			documents.add(Document.builder()
				.id(documentId)
				.text(chunks.get(index).text())
				.metadata(Map.ofEntries(
					Map.entry("origin", "OFFICIAL_REFERENCE"),
					Map.entry("title", metadata.entry().title()),
					Map.entry("sourceUrl", sourceUrl),
					Map.entry("pdfUrl", metadata.entry().pdfUrl().toString()),
					Map.entry("retrievedAt", metadata.retrievedAt().toString()),
					Map.entry("license", metadata.entry().license()),
					Map.entry("subject", metadata.entry().subject().name()),
					Map.entry("publicationDate", metadata.entry().publicationDate().toString()),
					Map.entry("sourceFile", metadata.sourceFile()),
					Map.entry("chapter", chunks.get(index).chapter()),
					Map.entry("class", metadata.entry().schoolClass()),
					Map.entry("schoolYears", String.join(", ", metadata.entry().schoolYears()))))
				.build());
		}

		vectorStore.add(documents);
		if (existing != null && existing.indexedChunks() > documents.size()) {
			List<String> staleDocumentIds = new ArrayList<>();
			for (int index = documents.size(); index < existing.indexedChunks(); index++) {
				staleDocumentIds.add(UUID.nameUUIDFromBytes(
					(sourceUrl + "#" + index).getBytes(StandardCharsets.UTF_8)).toString());
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
			sourceUrl,
			metadata.sourceFile(),
			metadata.entry().title(),
			metadata.retrievedAt(),
			metadata.entry().license(),
			metadata.entry().subject().name(),
			metadata.entry().publicationDate(),
			contentHash,
			documents.size());

		events.publishEvent(new ReferenceIndexed(
			referenceId, metadata.entry().title(), sourceUrl, documents.size()));
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
			ReferenceManifestEntry entry, LocalDate retrievedAt, String sourceFile) {
	}

	private record ExistingReference(String contentHash, int indexedChunks) {
	}

	private record ImportOutcome(boolean skipped, int indexedChunks) {
	}

}
