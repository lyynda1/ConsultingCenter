package com.advisora.enums;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    public static TaskStatus fromDb(String value) {
        if (value == null || value.isBlank()) {
            return TODO;
        }
        try {
            return TaskStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TODO;
        }
    }
}

