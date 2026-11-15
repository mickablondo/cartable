package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Définition rattachée à ses passages justificatifs.
 * @param term terme défini
 * @param definition définition
 * @param sourceIds identifiants des sources qui étayent la définition
 */
public record SheetDefinition(String term, String definition, List<String> sourceIds) {
}
