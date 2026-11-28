package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Théorème mathématique étayé par ses passages justificatifs.
 */
public record MathTheorem(String name, String statement, List<String> sourceIds) {
}
