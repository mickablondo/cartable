package dev.mickablondo.cartable.course;

/**
 * Texte transcrit d'une page à relire.
 * @param pageNumber numéro de la page
 * @param fileName nom du fichier image d'origine
 * @param extractedText texte transcrit
 */
public record CoursePageReview(int pageNumber, String fileName, String extractedText) {
}
