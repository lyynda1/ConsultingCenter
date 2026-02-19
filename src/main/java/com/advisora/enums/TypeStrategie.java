package com.advisora.enums;

public enum TypeStrategie {
    MARKETING,
    FINANCIERE,
    OPERATIONNELLE,
    DIGITALE,
    RH,
    CROISSANCE,
    COMMERCIALE,
    JURIDIQUE,
    NULL;

    public static TypeStrategie fromDb(String typeStrategie) {
        if (typeStrategie == null || typeStrategie.isBlank()) {
            return NULL;
        }
        try {
            return TypeStrategie.valueOf(typeStrategie.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NULL;
        }
    }
}
