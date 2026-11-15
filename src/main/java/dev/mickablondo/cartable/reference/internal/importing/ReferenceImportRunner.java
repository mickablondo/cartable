package dev.mickablondo.cartable.reference.internal.importing;

import dev.mickablondo.cartable.reference.ReferenceUpdateReport;
import dev.mickablondo.cartable.reference.ReferenceUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cartable.reference.import.enabled", havingValue = "true")
class ReferenceImportRunner implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(ReferenceImportRunner.class);

	private final ReferenceUpdateService referenceUpdateService;

	ReferenceImportRunner(ReferenceUpdateService referenceUpdateService) {
		this.referenceUpdateService = referenceUpdateService;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		ReferenceUpdateReport result = referenceUpdateService.updateReference();
		logger.info("Mise à jour du référentiel terminée : {} document(s) mis à jour, {} inchangé(s), "
				+ "{} passage(s), {} erreur(s).",
			result.updatedDocuments(), result.unchangedDocuments(), result.indexedChunks(), result.issues().size());
		result.issues().forEach(issue -> logger.warn("Document {} ({}): {}",
			issue.documentId(), issue.pdfUrl(), issue.reason()));
	}

}
