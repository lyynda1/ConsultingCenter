/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Service layer: business logic and SQL orchestration
*/
package com.advisora.Services;

import com.advisora.enums.UserRole;

public final class SessionContext {
    private static Integer currentUserId;
    private static UserRole currentRole;

    private SessionContext() {
    }

    public static void setCurrentUser(int idUser, UserRole role) {
        if (idUser <= 0) {
            throw new IllegalArgumentException("Invalid user id");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        currentUserId = idUser;
        currentRole = role;
    }

    public static int getCurrentUserId() {
        if (currentUserId == null) {
            throw new IllegalStateException("No user in session");
        }
        return currentUserId;
    }

    public static UserRole getCurrentRole() {
        if (currentRole == null) {
            throw new IllegalStateException("No role in session");
        }
        return currentRole;
    }

    public static boolean isClient() {
        return getCurrentRole() == UserRole.CLIENT;
    }

    public static boolean isAdmin() {
        return getCurrentRole() == UserRole.ADMIN;
    }

    public static boolean isGerant() {
        return getCurrentRole() == UserRole.GERANT;
    }

    public static void clear() {
        currentUserId = null;
        currentRole = null;
    }
}
