package com.advisora.enums;

public enum Severity {
    INFO, WARNING, DANGER, CRITICAL;

    public static Severity max(Severity a, Severity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

}
