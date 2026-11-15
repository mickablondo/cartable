package dev.mickablondo.cartable.sheet;

import java.util.List;

/**
 * Élément de fiche rattaché à ses passages justificatifs.
 * @param text contenu de l'élément
 * @param sourceIds identifiants des sources qui étayent l'élément
 */
public record SheetStatement(String text, List<String> sourceIds) {
}
