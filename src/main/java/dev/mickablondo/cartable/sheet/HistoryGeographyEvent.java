package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Événement historique rattaché à ses passages justificatifs.
 */
public record HistoryGeographyEvent(String date, String event, List<String> sourceIds) {
}
