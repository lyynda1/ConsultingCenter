package com.advisora.utils;

import com.advisora.utils.i18n.I18n;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class UserPreferencesManager {
    private static final String PREFS_FILE = "user_prefs.properties";
    private static final String KEY_THEME = "theme";
    private static final String KEY_FONT_SIZE = "fontSize";
    private static final String KEY_FONT_SIZE_PX = "fontSizePx";
    private static final String KEY_COMPACT = "compact";
    private static final String KEY_LANGUAGE = "language";
    private static final String CLASS_COMPACT = "compact-ui";
    private static final int TITLE_SIZE_DELTA = 16;
    private static final int MIN_FONT_SIZE = 10;
    private static final int MAX_FONT_SIZE = 24;
    private static final int DEFAULT_FONT_SIZE = 14;
    private static final Set<String> TITLE_CLASSES = Set.of(
            "title",
            "page-title",
            "module-title",
            "task-page-title",
            "stats-title",
            "gamehub-title",
            "home-hero-title"
    );

    private static final Object LOCK = new Object();
    private static volatile UserPreferences cached;
    private static volatile boolean windowHookInstalled = false;

    private UserPreferencesManager() {
    }

    public enum ThemeChoice {
        DARK,
        LIGHT,
        SYSTEM
    }

    public enum LanguageChoice {
        FR(Locale.FRENCH),
        EN(Locale.ENGLISH);

        private final Locale locale;

        LanguageChoice(Locale locale) {
            this.locale = locale;
        }

        public Locale locale() {
            return locale;
        }
    }

    public static final class UserPreferences {
        private final ThemeChoice theme;
        private final int fontSizePx;
        private final boolean compact;
        private final LanguageChoice language;

        public UserPreferences(ThemeChoice theme, int fontSizePx, boolean compact, LanguageChoice language) {
            this.theme = theme == null ? ThemeChoice.DARK : theme;
            this.fontSizePx = clampFontSize(fontSizePx);
            this.compact = compact;
            this.language = language == null ? LanguageChoice.FR : language;
        }

        public ThemeChoice theme() {
            return theme;
        }

        public int fontSizePx() {
            return fontSizePx;
        }

        public boolean compact() {
            return compact;
        }

        public LanguageChoice language() {
            return language;
        }
    }

    public static UserPreferences defaults() {
        return new UserPreferences(ThemeChoice.SYSTEM, DEFAULT_FONT_SIZE, false, LanguageChoice.FR);
    }

    public static UserPreferences get() {
        UserPreferences local = cached;
        if (local != null) {
            return local;
        }
        synchronized (LOCK) {
            if (cached == null) {
                cached = loadFromDisk();
            }
            return cached;
        }
    }

    public static void save(UserPreferences prefs) throws IOException {
        UserPreferences safe = prefs == null ? defaults() : prefs;
        synchronized (LOCK) {
            Properties props = new Properties();
            props.setProperty(KEY_THEME, safe.theme().name());
            props.setProperty(KEY_FONT_SIZE_PX, String.valueOf(safe.fontSizePx()));
            props.remove(KEY_FONT_SIZE);
            props.setProperty(KEY_COMPACT, String.valueOf(safe.compact()));
            props.setProperty(KEY_LANGUAGE, safe.language().name());

            Path path = prefsPath();
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "User preferences");
            }
            cached = safe;
        }
    }

    public static void saveAndApplyAll(UserPreferences prefs) throws IOException {
        save(prefs);
        applyToAllWindows(prefs);
    }

    public static void applySavedToScene(Scene scene) {
        ensureWindowHookInstalled();
        applyToScene(scene, get());
    }

    public static void applyToAllWindows(UserPreferences prefs) {
        ensureWindowHookInstalled();
        for (Window window : Window.getWindows()) {
            if (window == null) {
                continue;
            }
            Scene scene = window.getScene();
            if (scene != null) {
                applyToScene(scene, prefs);
            }
        }
    }

    public static void applyToScene(Scene scene, UserPreferences prefs) {
        if (scene == null) {
            return;
        }
        UserPreferences safe = prefs == null ? defaults() : prefs;

        ThemeMode resolved = resolveTheme(safe.theme());
        ThemeManager.applyTheme(scene, resolved);

        Parent root = scene.getRoot();
        if (root == null) {
            return;
        }

        if (safe.compact()) {
            if (!root.getStyleClass().contains(CLASS_COMPACT)) {
                root.getStyleClass().add(CLASS_COMPACT);
            }
        } else {
            root.getStyleClass().remove(CLASS_COMPACT);
        }

        applyStyleSafely(root, upsertStyleProperty(root.getStyle(), "-fx-font-size", safe.fontSizePx() + "px"));
        try {
            applyFontSizeToSceneNodes(root, safe.fontSizePx());
        } catch (Exception ignored) {
            // Best-effort font scaling only.
        }
    }

    public static Locale resolveLocale(UserPreferences prefs) {
        UserPreferences safe = prefs == null ? defaults() : prefs;
        return safe.language().locale();
    }

    public static Locale resolveLocaleFromSavedPrefs() {
        return resolveLocale(get());
    }

    public static void applySavedLocale() {
        I18n.setLocale(resolveLocaleFromSavedPrefs());
    }

    private static ThemeMode resolveTheme(ThemeChoice choice) {
        if (choice == null) {
            return ThemeMode.DARK;
        }
        if (choice == ThemeChoice.LIGHT) {
            return ThemeMode.LIGHT;
        }
        if (choice == ThemeChoice.DARK) {
            return ThemeMode.DARK;
        }
        return ThemeManager.getCurrentMode();
    }

    private static UserPreferences loadFromDisk() {
        Path path = prefsPath();
        if (!Files.exists(path)) {
            return defaults();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (Exception ignored) {
            return defaults();
        }

        ThemeChoice theme = parseEnum(props.getProperty(KEY_THEME), ThemeChoice.class, ThemeChoice.DARK);
        int fontSizePx = parseFontSizePx(props);
        boolean compact = Boolean.parseBoolean(props.getProperty(KEY_COMPACT, "false"));
        LanguageChoice lang = parseEnum(props.getProperty(KEY_LANGUAGE), LanguageChoice.class, LanguageChoice.FR);
        return new UserPreferences(theme, fontSizePx, compact, lang);
    }

    private static Path prefsPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".advisora", PREFS_FILE);
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private static int parseFontSizePx(Properties props) {
        String explicit = props.getProperty(KEY_FONT_SIZE_PX);
        if (explicit != null && !explicit.isBlank()) {
            try {
                return clampFontSize(Integer.parseInt(explicit.trim()));
            } catch (Exception ignored) {
                return DEFAULT_FONT_SIZE;
            }
        }

        // Backward compatibility with old enum storage.
        String legacy = props.getProperty(KEY_FONT_SIZE, "MEDIUM").trim().toUpperCase(Locale.ROOT);
        return switch (legacy) {
            case "SMALL" -> 12;
            case "LARGE" -> 16;
            default -> DEFAULT_FONT_SIZE;
        };
    }

    private static int clampFontSize(int value) {
        if (value < MIN_FONT_SIZE) return MIN_FONT_SIZE;
        if (value > MAX_FONT_SIZE) return MAX_FONT_SIZE;
        return value;
    }

    private static void ensureWindowHookInstalled() {
        if (windowHookInstalled) {
            return;
        }
        synchronized (UserPreferencesManager.class) {
            if (windowHookInstalled) {
                return;
            }
            Window.getWindows().addListener((ListChangeListener<Window>) change -> {
                while (change.next()) {
                    if (!change.wasAdded()) {
                        continue;
                    }
                    for (Window window : change.getAddedSubList()) {
                        if (window == null) {
                            continue;
                        }
                        window.sceneProperty().addListener((obs, oldScene, newScene) -> {
                            if (newScene != null) {
                                applyToScene(newScene, get());
                            }
                        });
                        Scene scene = window.getScene();
                        if (scene != null) {
                            applyToScene(scene, get());
                        }
                    }
                }
            });
            windowHookInstalled = true;
        }
    }

    private static void applyFontSizeToSceneNodes(Parent root, int baseFontPx) {
        if (root == null) {
            return;
        }

        Set<Node> nodes = new HashSet<>();
        nodes.add(root);
        nodes.addAll(root.lookupAll("*"));

        for (Node node : nodes) {
            if (!(node instanceof Control) && !(node instanceof Labeled)) {
                continue;
            }

            if (isTitleNode(node)) {
                int titlePx = clampFontSize(baseFontPx) + TITLE_SIZE_DELTA;
                String styled = upsertStyleProperty(node.getStyle(), "-fx-font-size", titlePx + "px");
                applyStyleSafely(node, upsertStyleProperty(styled, "-fx-font-weight", "900"));
                continue;
            }

            if (node instanceof TextInputControl
                    || node instanceof ComboBoxBase<?>
                    || node instanceof DatePicker
                    || node instanceof Spinner<?>
                    || node instanceof TitledPane
                    || node instanceof Labeled) {
                applyStyleSafely(node, upsertStyleProperty(node.getStyle(), "-fx-font-size", clampFontSize(baseFontPx) + "px"));
            }
        }
    }

    private static void applyStyleSafely(Node node, String style) {
        if (node == null) {
            return;
        }
        try {
            if (node.styleProperty().isBound()) {
                return;
            }
            node.setStyle(style);
        } catch (RuntimeException ignored) {
            // Some JavaFX skin internals expose bound style properties (e.g. popup menu labels).
            // Preferences application must stay best-effort and never break the UI thread.
        }
    }

    private static boolean isTitleNode(Node node) {
        for (String styleClass : node.getStyleClass()) {
            if (TITLE_CLASSES.contains(styleClass)) {
                return true;
            }
        }
        return false;
    }

    private static String upsertStyleProperty(String style, String key, String value) {
        String safeStyle = style == null ? "" : style;
        String targetKey = Objects.requireNonNull(key).trim().toLowerCase(Locale.ROOT);

        StringBuilder out = new StringBuilder();
        for (String part : safeStyle.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int idx = trimmed.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String k = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            if (!k.equals(targetKey)) {
                out.append(trimmed).append("; ");
            }
        }
        out.append(key).append(": ").append(value).append(";");
        return out.toString();
    }
}
