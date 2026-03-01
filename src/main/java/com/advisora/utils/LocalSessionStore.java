package com.advisora.utils;

import java.nio.file.*;

public class LocalSessionStore {
    private static final Path FILE =
            Paths.get(System.getProperty("user.home"), ".advisora", "session.token");

    public static void save(String token) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, token, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Save session failed: " + e.getMessage(), e);
        }
    }

    public static String load() {
        try {
            if (!Files.exists(FILE)) return null;
            String t = Files.readString(FILE).trim();
            return t.isEmpty() ? null : t;
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() {
        try { Files.deleteIfExists(FILE); } catch (Exception ignored) {}
    }
}
