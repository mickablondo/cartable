package dev.mickablondo.cartable.course;

/**
 * Image d'une page de cours à relire.
 * @param content contenu de l'image
 * @param mediaType type MIME de l'image
 */
public record CoursePageImage(byte[] content, String mediaType) {
}
