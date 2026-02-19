/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Service layer: business logic and SQL orchestration
*/
package com.advisora.Services;

import com.advisora.Model.User;
import com.advisora.enums.UserRole;

public final class SessionContext {

    private static Integer currentUserId;
    private static UserRole currentRole;

    // NEW: store full user object (name, imagePath, email, ...)
    private static User currentUser;

    private SessionContext() {}

    // keep your existing method (backward compatible)
    public static void setCurrentUser(int idUser, UserRole role) {
        if (idUser <= 0) throw new IllegalArgumentException("Invalid user id");
        if (role == null) throw new IllegalArgumentException("Role is required");

        currentUserId = idUser;
        currentRole = role;
        currentUser = null; // we don't know full details here
    }

    // NEW: preferred method
    public static void setCurrentUser(User user) {
        if (user == null) throw new IllegalArgumentException("User is required");
        if (user.getId() <= 0) throw new IllegalArgumentException("Invalid user id");
        if (user.getRole() == null) throw new IllegalArgumentException("Role is required");

        currentUser = user;
        currentUserId = user.getId();
        currentRole = user.getRole();
    }

    public static User getCurrentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("No user details in session (use setCurrentUser(User))");
        }
        return currentUser;
    }

    public static int getCurrentUserId() {
        if (currentUserId == null) throw new IllegalStateException("No user in session");
        return currentUserId;
    }

    public static UserRole getCurrentRole() {
        if (currentRole == null) throw new IllegalStateException("No role in session");
        return currentRole;
    }

    public static boolean isClient() {
        return getCurrentRole() == UserRole.CLIENT;
    }

    public static boolean isManager() {
        return isGerant();
    }

    public static boolean isGerant() {
        return getCurrentRole() == UserRole.GERANT;
    }

    public static void clear() {
        currentUserId = null;
        currentRole = null;
        currentUser = null;
    }
}
