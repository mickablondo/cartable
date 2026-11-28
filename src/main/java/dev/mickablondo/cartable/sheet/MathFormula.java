package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Formule mathématique avec son avertissement éventuel de vérification.
 */
public record MathFormula(
		String name, String latex, String explanation, List<String> sourceIds, boolean requiresVerification) {
}
