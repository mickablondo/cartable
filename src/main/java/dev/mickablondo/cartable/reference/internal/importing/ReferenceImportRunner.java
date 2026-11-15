package dev.mickablondo.cartable.reference.internal.importing;

import dev.mickablondo.cartable.reference.ReferenceImportResult;
import dev.mickablondo.cartable.reference.ReferenceImportService;
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

	private final ReferenceImportService referenceImportService;

	ReferenceImportRunner(ReferenceImportService referenceImportService) {
		this.referenceImportService = referenceImportService;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		ReferenceImportResult result = referenceImportService.importReferences();
		logger.info("Import du référentiel terminé : {} indexé(s), {} déjà à jour, {} passage(s).",
			result.importedDocuments(), result.skippedDocuments(), result.indexedChunks());
	}

}
