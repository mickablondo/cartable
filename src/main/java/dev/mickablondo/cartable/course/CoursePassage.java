package dev.mickablondo.cartable.course;

/**
 * Passage retrouvé dans un cours avec sa référence.
 * @param text texte du passage
 * @param sourceFile nom du fichier du cours
 * @param chapter chapitre du cours
 */
public record CoursePassage(String text, String sourceFile, String chapter) {
}
