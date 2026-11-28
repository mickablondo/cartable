package dev.mickablondo.cartable.sheet.internal.model;

import java.util.List;

/**
 * Formule retournée par le modèle avant ajout des avertissements vérifiés par le code.
 */
public record MathFormulaDraft(String name, String latex, String explanation, List<String> sourceIds) {
}
