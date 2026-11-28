package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Erreur fréquente et correction étayées par les passages du cours.
 */
public record MathCommonError(String error, String correction, List<String> sourceIds) {
}
