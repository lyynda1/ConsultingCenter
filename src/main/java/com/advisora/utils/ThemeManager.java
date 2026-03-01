package com.advisora.utils;

import javafx.scene.Scene;
import javafx.scene.Parent;

import java.util.prefs.Preferences;

public final class ThemeManager {
    private static final String PREF_NODE = "com.advisora.ui";
    private static final String PREF_KEY_MODE = "themeMode";
    private static final String BASE_STYLESHEET = "/css/theme-base.css";
    private static final String DARK_STYLESHEET = "/css/theme-dark.css";
    private static final String CLASS_LIGHT = "theme-light";
    private static final String CLASS_DARK = "theme-dark";
    private static final Preferences PREFS = Preferences.userRoot().node(PREF_NODE);

    private ThemeManager() {
    }

    public static ThemeMode getCurrentMode() {
        String raw = PREFS.get(PREF_KEY_MODE, ThemeMode.LIGHT.name());
        try {
            return ThemeMode.valueOf(raw);
        } catch (Exception ignored) {
            return ThemeMode.LIGHT;
        }
    }

    public static boolean isDarkMode() {
        return getCurrentMode() == ThemeMode.DARK;
    }

    public static void applySavedTheme(Scene scene) {
        applyTheme(scene, getCurrentMode());
    }

    public static void applyTheme(Scene scene, ThemeMode mode) {
        if (scene == null) return;

        ThemeMode safeMode = mode == null ? ThemeMode.LIGHT : mode;
        PREFS.put(PREF_KEY_MODE, safeMode.name());

        String baseUrl = ThemeManager.class.getResource(BASE_STYLESHEET).toExternalForm();
        String darkUrl = ThemeManager.class.getResource(DARK_STYLESHEET).toExternalForm();

        if (!scene.getStylesheets().contains(baseUrl)) {
            scene.getStylesheets().add(baseUrl);
        }

        Parent root = scene.getRoot();
        if (root != null) {
            root.getStyleClass().removeAll(CLASS_LIGHT, CLASS_DARK);
            root.getStyleClass().add(safeMode == ThemeMode.DARK ? CLASS_DARK : CLASS_LIGHT);
        }

        if (safeMode == ThemeMode.DARK) {
            if (!scene.getStylesheets().contains(darkUrl)) {
                scene.getStylesheets().add(darkUrl);
            }
        } else {
            scene.getStylesheets().remove(darkUrl);
        }
    }

    public static ThemeMode toggleMode(Scene scene) {
        ThemeMode next = getCurrentMode() == ThemeMode.DARK ? ThemeMode.LIGHT : ThemeMode.DARK;
        applyTheme(scene, next);
        return next;
    }

    public static boolean toggle(Scene scene) {
        return toggleMode(scene) == ThemeMode.DARK;
    }

    public static void setDarkMode(Scene scene, boolean dark) {
        applyTheme(scene, dark ? ThemeMode.DARK : ThemeMode.LIGHT);
    }
}

