package com.advisora.enums;

public enum RessourceStatut {
    AVAILABLE,
    RESERVED,
    UNAVAILABLE;

    public static RessourceStatut fromDb(String value) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE;
        }
        String v = value.trim().toUpperCase();
        if (v.equals("DISPONIBLE") || v.equals("AVAILABLE")) {
            return AVAILABLE;
        }
        if (v.equals("RESERVE") || v.equals("RESERVED")) {
            return RESERVED;
        }
        if (v.equals("INDISPONIBLE") || v.equals("UNAVAILABLE")) {
            return UNAVAILABLE;
        }
        return UNAVAILABLE;
    }
}

