package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Repère spatial rattaché à ses passages justificatifs.
 */
public record HistoryGeographyLandmark(String place, List<String> sourceIds) {
}
