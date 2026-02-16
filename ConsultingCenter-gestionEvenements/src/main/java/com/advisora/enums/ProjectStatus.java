/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Enum/type constants used for application state
*/
package com.advisora.enums;

public enum ProjectStatus {
    PENDING,
    ACCEPTED,
    REFUSED,
    ARCHIVED;

    public static ProjectStatus fromDb(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return ProjectStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
