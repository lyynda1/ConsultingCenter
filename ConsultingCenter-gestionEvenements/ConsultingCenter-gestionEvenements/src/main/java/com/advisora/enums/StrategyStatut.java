/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Enum/type constants used for application state
*/
package com.advisora.enums;

import java.text.Normalizer;
import java.util.Locale;

public enum StrategyStatut {
    EN_COURS("En_cours"),
    ACCEPTEE("Acceptee"),
    REFUSEE("Refusee"),
    EN_ATTENTE("En_attente"),
    Non_affectée("Non_affectée");

    private final String dbValue;

    StrategyStatut(String dbValue) {
        this.dbValue = dbValue;
    }

    public String toDb() {
        return dbValue;
    }

    public static StrategyStatut fromDb(String value) {
        if (value == null || value.isBlank()) {
            return EN_COURS;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('-', '_')
                .trim()
                .toUpperCase(Locale.ROOT);
        if (normalized.contains("EN_COURS") || normalized.contains("ENCOURS")) {
            return EN_COURS;
        }
        if (normalized.contains("ACCEP")) {
            return ACCEPTEE;
        }
        if (normalized.contains("REFUS")) {
            return REFUSEE;
        }
        if (normalized.contains("EN_ATTENTE") || normalized.contains("ENATTENTE")) {
            return EN_ATTENTE;
        }
        if (normalized.contains("NON_AFFECTE") || normalized.contains("NONA FFECTE") || normalized.contains("NON_AFFECTE") || normalized.contains("NONAFFECTE")) {
            return Non_affectée;
        }
        return switch (normalized) {
            case "EN_COURS" -> EN_COURS;
            case "ACCEPTEE", "ACCEPTEE " -> ACCEPTEE;
            case "REFUSEE" -> REFUSEE;
            case "EN_ATTENTE" -> EN_ATTENTE;
            case "NON_AFFECTE", "NONAFFECTE" -> Non_affectée;
            default -> EN_COURS;
        };
    }
}
