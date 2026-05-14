package com.advisora.enums;

public enum ScopeSize {
    S,
    M,
    L;

    public static ScopeSize fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("scope is required (S/M/L)");
        }
        try {
            return ScopeSize.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scope must be one of S/M/L");
        }
    }
}

