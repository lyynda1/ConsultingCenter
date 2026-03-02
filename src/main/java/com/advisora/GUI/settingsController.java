package com.advisora.GUI;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class settingsController {

    @FXML private ComboBox<ThemeMode> cmbTheme;
    @FXML private ComboBox<FontSizeMode> cmbFontSize;
    @FXML private CheckBox chkCompact;
    @FXML private ComboBox<LanguageMode> cmbLanguage;
    @FXML private Label lblFontPreview;
    @FXML private Label lblSaved;

    private static final String PREFS_FILE = "user_prefs.properties";
    private final Properties props = new Properties();

    private UserPrefs loadedPrefs;

    // ===================== enums =====================
    public enum ThemeMode {
        DARK("Sombre"),
        LIGHT("Clair"),
        SYSTEM("Système");
        public final String label;
        ThemeMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum FontSizeMode {
        SMALL("Petite", 12),
        MEDIUM("Moyenne", 14),
        LARGE("Grande", 16);
        public final String label;
        public final int px;
        FontSizeMode(String label, int px) { this.label = label; this.px = px; }
        @Override public String toString() { return label; }
    }

    public enum LanguageMode {
        FR("Français", Locale.FRENCH),
        EN("English", Locale.ENGLISH);
        public final String label;
        public final Locale locale;
        LanguageMode(String label, Locale locale) { this.label = label; this.locale = locale; }
        @Override public String toString() { return label; }
    }

    // ===================== DTO =====================
    public static class UserPrefs {
        public ThemeMode theme = ThemeMode.DARK;
        public FontSizeMode fontSize = FontSizeMode.MEDIUM;
        public boolean compact = false;
        public LanguageMode language = LanguageMode.FR;
    }

    // ===================== init =====================
    @FXML
    public void initialize() {
        cmbTheme.getItems().setAll(ThemeMode.values());
        cmbFontSize.getItems().setAll(FontSizeMode.values());
        cmbLanguage.getItems().setAll(LanguageMode.values());

        loadedPrefs = loadPrefs();
        setUiFromPrefs(loadedPrefs);

        cmbFontSize.valueProperty().addListener((obs, o, nv) -> updateFontPreview(nv));

        // Live apply (UX pro)
        cmbTheme.valueProperty().addListener((obs, o, nv) -> applyLive());
        cmbFontSize.valueProperty().addListener((obs, o, nv) -> applyLive());
        chkCompact.selectedProperty().addListener((obs, o, nv) -> applyLive());
        // Language: usually needs reloading views; we store it but don’t force-refresh everything here.

        updateFontPreview(cmbFontSize.getValue());
        lblSaved.setText("");
    }

    private void applyLive() {
        Scene scene = getSceneSafe();
        if (scene != null) applyToScene(readPrefsFromUi(), scene);
    }

    private Scene getSceneSafe() {
        // Any control can give us the scene once attached
        if (cmbTheme != null && cmbTheme.getScene() != null) return cmbTheme.getScene();
        if (chkCompact != null && chkCompact.getScene() != null) return chkCompact.getScene();
        return null;
    }

    // ===================== actions =====================
    @FXML
    private void onResetDefaults() {
        UserPrefs d = new UserPrefs();
        setUiFromPrefs(d);
        updateFontPreview(d.fontSize);

        Scene scene = getSceneSafe();
        if (scene != null) applyToScene(d, scene);

        lblSaved.setText("Paramètres réinitialisés (non enregistrés).");
    }

    @FXML
    private void onSave() {
        UserPrefs p = readPrefsFromUi();

        try {
            savePrefs(p);
            loadedPrefs = p;

            Scene scene = getSceneSafe();
            if (scene != null) applyToScene(p, scene);

            lblSaved.setText("✅ Paramètres enregistrés.");
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Impossible d'enregistrer: " + e.getMessage(), ButtonType.OK)
                    .showAndWait();
        }
    }

    // ===================== UI <-> Prefs =====================
    private void setUiFromPrefs(UserPrefs p) {
        cmbTheme.setValue(p.theme);
        cmbFontSize.setValue(p.fontSize);
        chkCompact.setSelected(p.compact);
        cmbLanguage.setValue(p.language);
    }

    private UserPrefs readPrefsFromUi() {
        UserPrefs p = new UserPrefs();
        p.theme = Objects.requireNonNullElse(cmbTheme.getValue(), ThemeMode.DARK);
        p.fontSize = Objects.requireNonNullElse(cmbFontSize.getValue(), FontSizeMode.MEDIUM);
        p.compact = chkCompact.isSelected();
        p.language = Objects.requireNonNullElse(cmbLanguage.getValue(), LanguageMode.FR);
        return p;
    }

    private void updateFontPreview(FontSizeMode fs) {
        FontSizeMode x = (fs == null) ? FontSizeMode.MEDIUM : fs;
        lblFontPreview.setStyle("-fx-font-size: " + x.px + "px; -fx-font-weight: 800;");
        lblFontPreview.setText("Aperçu (" + x.px + "px)");
    }

    // ===================== Apply styles =====================
    private void applyToScene(UserPrefs p, Scene scene) {
        if (scene == null) return;

        scene.getStylesheets().removeIf(s ->
                s.contains("theme-dark.css") ||
                        s.contains("theme-light.css") ||
                        s.contains("compact.css")
        );

        ThemeMode theme = p.theme;
        if (theme == ThemeMode.SYSTEM) theme = ThemeMode.DARK; // fallback simple

        String themeCss = (theme == ThemeMode.LIGHT)
                ? "/styles/theme-light.css"
                : "/styles/theme-dark.css";
        safeAddStylesheet(scene, themeCss);

        if (p.compact) safeAddStylesheet(scene, "/styles/compact.css");

        if (scene.getRoot() != null) {
            scene.getRoot().setStyle("-fx-font-size: " + p.fontSize.px + "px;");
        }
    }

    private void safeAddStylesheet(Scene scene, String resourcePath) {
        var url = getClass().getResource(resourcePath);
        if (url != null) {
            String ext = url.toExternalForm();
            if (!scene.getStylesheets().contains(ext)) scene.getStylesheets().add(ext);
        }
    }

    // ===================== Storage =====================
    private UserPrefs loadPrefs() {
        UserPrefs p = new UserPrefs();
        Path path = prefsPath();
        if (!Files.exists(path)) return p;

        try (InputStream in = Files.newInputStream(path)) {
            props.clear();
            props.load(in);

            p.theme = parseEnum(props.getProperty("theme"), ThemeMode.class, ThemeMode.DARK);
            p.fontSize = parseEnum(props.getProperty("fontSize"), FontSizeMode.class, FontSizeMode.MEDIUM);
            p.compact = Boolean.parseBoolean(props.getProperty("compact", "false"));
            p.language = parseEnum(props.getProperty("language"), LanguageMode.class, LanguageMode.FR);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return p;
    }

    private void savePrefs(UserPrefs p) throws IOException {
        props.setProperty("theme", p.theme.name());
        props.setProperty("fontSize", p.fontSize.name());
        props.setProperty("compact", String.valueOf(p.compact));
        props.setProperty("language", p.language.name());

        Path path = prefsPath();
        Files.createDirectories(path.getParent());

        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "User preferences");
        }
    }

    private Path prefsPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".advisora", PREFS_FILE);
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E def) {
        if (raw == null) return def;
        try { return Enum.valueOf(type, raw.trim()); }
        catch (Exception e) { return def; }
    }
}