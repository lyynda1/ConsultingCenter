package com.advisora.enums;

public enum SWOTType {
    STRENGTH, WEAKNESS, OPPORTUNITY, THREAT;

    public String labelFR() {
        return switch (this) {
            case STRENGTH -> "Forces";
            case WEAKNESS -> "Faiblesses";
            case OPPORTUNITY -> "Opportunités";
            case THREAT -> "Menaces";
        };
    }
}