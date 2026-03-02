package com.advisora.GUI;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
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
        FR("language.fr", Locale.FRENCH),
        EN("language.en", Locale.ENGLISH);

        private final String key;
        public final Locale locale;

        LanguageMode(String key, Locale locale) {
            this.key = key;
            this.locale = locale;
        }

        @Override
        public String toString() {
            return com.advisora.utils.i18n.I18n.tr(key);
        }
    }

    // ===================== DTO =====================
    public static class UserPrefs {
        public ThemeMode theme = ThemeMode.DARK;
        public FontSizeMode fontSize = FontSizeMode.MEDIUM;
        public boolean compact = false;
        public LanguageMode language = LanguageMode.FR;
    }

    // ===================== init =====================
    private boolean initializing = false;

    @FXML
    public void initialize() {
        initializing = true;

        cmbTheme.getItems().setAll(ThemeMode.values());
        cmbFontSize.getItems().setAll(FontSizeMode.values());
        cmbLanguage.getItems().setAll(LanguageMode.values());

        loadedPrefs = loadPrefs();
        setUiFromPrefs(loadedPrefs);

        updateFontPreview(cmbFontSize.getValue());
        lblSaved.setText("");

        // listeners
        cmbFontSize.valueProperty().addListener((obs, o, nv) -> updateFontPreview(nv));

        cmbTheme.valueProperty().addListener((obs, o, nv) -> { if (!initializing) applyLive(); });
        cmbFontSize.valueProperty().addListener((obs, o, nv) -> { if (!initializing) applyLive(); });
        chkCompact.selectedProperty().addListener((obs, o, nv) -> { if (!initializing) applyLive(); });

        cmbLanguage.valueProperty().addListener((obs, oldV, newV) -> {
            if (initializing) return;
            if (newV == null || newV == oldV) return;
            onLanguageSelected(newV);
        });

        initializing = false;
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
    private void onLanguageSelected(LanguageMode nv) {
        if (nv == null) nv = LanguageMode.FR;

        UserPrefs p = readPrefsFromUi();
        p.language = nv;
        try {
            savePrefs(p);
            loadedPrefs = p;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // ✅ single source of truth
        com.advisora.utils.i18n.I18n.setLocale(nv.locale);

        // ✅ easiest: reload main window so all %keys in FXML update
        reloadMainWindow();
    }
    private void reloadMainWindow() {
        try {
            Scene scene = cmbLanguage.getScene();
            if (scene == null) return;

            var stage = (javafx.stage.Stage) scene.getWindow();
            if (stage == null) return;

            double w = stage.getWidth();
            double h = stage.getHeight();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/InterfaceGeneral.fxml"),
                    com.advisora.utils.i18n.I18n.bundle()
            );
            Parent root = loader.load();

            stage.setScene(new Scene(root, w, h));

        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Language reload failed: " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void reloadSettingsIntoContentHost() {
        try {
            var scene = cmbLanguage.getScene();
            if (scene == null) return;

            // contentHost is in InterfaceGeneral.fxml
            StackPane host = (StackPane) scene.lookup("#contentHost");
            if (host == null) return;

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/parametres.fxml"),
                    com.advisora.utils.i18n.I18n.bundle()
            );
            Parent settings = loader.load();
            host.getChildren().setAll(settings);

        } catch (Exception e) {
            e.printStackTrace();
        }
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