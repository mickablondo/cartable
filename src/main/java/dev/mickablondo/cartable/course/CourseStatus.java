package dev.mickablondo.cartable.course;

public enum CourseStatus {

	IMPORTED("En attente d'indexation"),
	TEXT_EXTRACTION_IN_PROGRESS("Extraction du texte en cours"),
	TEXT_TO_REVIEW("Texte à vérifier"),
	VALIDATED("Texte validé, en attente d'indexation"),
	INDEXED("Indexé"),
	FAILED("Échec");

	private final String label;

	CourseStatus(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
