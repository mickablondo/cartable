package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Propriété mathématique étayée par ses passages justificatifs.
 */
public record MathProperty(String name, String statement, List<String> sourceIds) {
}
