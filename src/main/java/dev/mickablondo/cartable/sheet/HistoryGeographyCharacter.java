package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Personnage historique rattaché à ses passages justificatifs.
 */
public record HistoryGeographyCharacter(String name, String role, List<String> sourceIds) {
}
