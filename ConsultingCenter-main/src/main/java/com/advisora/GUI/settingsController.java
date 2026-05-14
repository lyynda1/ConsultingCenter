package com.advisora.GUI;

import com.advisora.utils.ThemeManager;
import com.advisora.utils.UserPreferencesManager;
import com.advisora.utils.i18n.I18n;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.Objects;

public class settingsController {

    @FXML private ComboBox<ThemeMode> cmbTheme;
    @FXML private ComboBox<Integer> cmbFontSize;
    @FXML private CheckBox chkCompact;
    @FXML private ComboBox<LanguageMode> cmbLanguage;
    @FXML private Label lblFontPreview;
    @FXML private Label lblSaved;

    private boolean initializing = false;

    public enum ThemeMode {
        DARK("settings.theme.dark"),
        LIGHT("settings.theme.light"),
        SYSTEM("settings.theme.system");

        private final String i18nKey;

        ThemeMode(String i18nKey) {
            this.i18nKey = i18nKey;
        }

        public String label() {
            return I18n.tr(i18nKey);
        }

        @Override
        public String toString() {
            return label();
        }
    }

    public enum LanguageMode {
        FR("language.fr", Locale.FRENCH),
        EN("language.en", Locale.ENGLISH);

        private final String i18nKey;
        private final Locale locale;

        LanguageMode(String i18nKey, Locale locale) {
            this.i18nKey = i18nKey;
            this.locale = locale;
        }

        public Locale locale() {
            return locale;
        }

        @Override
        public String toString() {
            return I18n.tr(i18nKey);
        }
    }

    @FXML
    public void initialize() {
        initializing = true;

        setupComboRendering();

        cmbTheme.getItems().setAll(ThemeMode.values());
        for (int i = 10; i <= 24; i++) {
            cmbFontSize.getItems().add(i);
        }
        cmbFontSize.setEditable(true);
        if (cmbFontSize.getEditor() != null) {
            cmbFontSize.getEditor().setTextFormatter(new TextFormatter<>(change -> {
                String next = change.getControlNewText();
                return next.matches("\\d{0,2}") ? change : null;
            }));
            cmbFontSize.getEditor().focusedProperty().addListener((obs, oldV, focused) -> {
                if (!focused) {
                    commitEditorFontSize();
                }
            });
            cmbFontSize.getEditor().setOnAction(e -> commitEditorFontSize());
        }
        cmbLanguage.getItems().setAll(LanguageMode.values());

        setUiFromPrefs(UserPreferencesManager.get());
        updateFontPreview(cmbFontSize.getValue());
        lblSaved.setText("");

        cmbFontSize.valueProperty().addListener((obs, o, nv) -> updateFontPreview(nv));

        cmbTheme.valueProperty().addListener((obs, o, nv) -> {
            if (!initializing) {
                applyLive();
            }
        });
        cmbFontSize.valueProperty().addListener((obs, o, nv) -> {
            if (!initializing) {
                applyLive();
            }
        });
        chkCompact.selectedProperty().addListener((obs, o, nv) -> {
            if (!initializing) {
                applyLive();
            }
        });

        cmbLanguage.valueProperty().addListener((obs, oldV, newV) -> {
            if (initializing || newV == null || newV == oldV) {
                return;
            }
            onLanguageSelected(newV);
        });

        initializing = false;
    }

    private void setupComboRendering() {
        cmbTheme.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ThemeMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        cmbTheme.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ThemeMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });

        cmbFontSize.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item + " px");
            }
        });
        cmbFontSize.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item + " px");
            }
        });
        cmbFontSize.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : String.valueOf(value);
            }

            @Override
            public Integer fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                String digits = text.replaceAll("[^0-9]", "");
                if (digits.isBlank()) {
                    return null;
                }
                try {
                    return normalizeFontSize(Integer.parseInt(digits));
                } catch (Exception ignored) {
                    return null;
                }
            }
        });

        cmbLanguage.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LanguageMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        cmbLanguage.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(LanguageMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
    }

    private void applyLive() {
        UserPreferencesManager.UserPreferences prefs = readPrefsFromUi();
        UserPreferencesManager.applyToAllWindows(prefs);
    }

    @FXML
    private void onResetDefaults() {
        initializing = true;
        UserPreferencesManager.UserPreferences defaults = UserPreferencesManager.defaults();
        setUiFromPrefs(defaults);
        updateFontPreview(cmbFontSize.getValue());
        initializing = false;

        UserPreferencesManager.applyToAllWindows(defaults);
        lblSaved.setText(I18n.tr("settings.msg.resetTemp"));
    }

    @FXML
    private void onSave() {
        UserPreferencesManager.UserPreferences prefs = readPrefsFromUi();
        try {
            UserPreferencesManager.saveAndApplyAll(prefs);
            lblSaved.setText(I18n.tr("settings.msg.saved"));
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, I18n.tr("settings.msg.saveFail") + ": " + e.getMessage(), ButtonType.OK)
                    .showAndWait();
        }
    }

    private void setUiFromPrefs(UserPreferencesManager.UserPreferences prefs) {
        cmbTheme.setValue(toUiTheme(prefs.theme()));
        cmbFontSize.setValue(prefs.fontSizePx());
        chkCompact.setSelected(prefs.compact());
        cmbLanguage.setValue(toUiLang(prefs.language()));
    }

    private UserPreferencesManager.UserPreferences readPrefsFromUi() {
        ThemeMode uiTheme = Objects.requireNonNullElse(cmbTheme.getValue(), ThemeMode.DARK);
        LanguageMode uiLang = Objects.requireNonNullElse(cmbLanguage.getValue(), LanguageMode.FR);
        int fontSizePx = readFontSizePxFromUi();

        return new UserPreferencesManager.UserPreferences(
                toCoreTheme(uiTheme),
                fontSizePx,
                chkCompact.isSelected(),
                toCoreLang(uiLang)
        );
    }

    private void updateFontPreview(Integer fontSizePx) {
        int px = normalizeFontSize(fontSizePx);
        lblFontPreview.setStyle("-fx-font-size: " + px + "px; -fx-font-weight: 800;");
        lblFontPreview.setText(I18n.tr("settings.appearance.preview") + " (" + px + "px)");
    }

    private void onLanguageSelected(LanguageMode selected) {
        if (selected == null) {
            return;
        }

        UserPreferencesManager.UserPreferences prefs = readPrefsFromUi();
        try {
            UserPreferencesManager.save(prefs);
        } catch (Exception e) {
            e.printStackTrace();
        }

        I18n.setLocale(selected.locale());
        reloadMainWindow();
    }

    private void reloadMainWindow() {
        try {
            Scene scene = cmbLanguage.getScene();
            if (scene == null) {
                return;
            }
            Stage stage = (Stage) scene.getWindow();
            if (stage == null) {
                return;
            }

            double width = stage.getWidth();
            double height = stage.getHeight();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/InterfaceGeneral.fxml"),
                    I18n.bundle()
            );
            Parent root = loader.load();

            Scene newScene = new Scene(root, width, height);
            stage.setScene(newScene);

            UserPreferencesManager.applySavedToScene(newScene);
            refreshTopThemeLabel(newScene);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, I18n.tr("settings.msg.reloadFail") + ": " + e.getMessage(), ButtonType.OK)
                    .showAndWait();
        }
    }

    private void refreshTopThemeLabel(Scene scene) {
        if (scene == null) {
            return;
        }
        var node = scene.lookup("#themeToggleBtn");
        if (node instanceof javafx.scene.control.Button btn) {
            btn.setText(ThemeManager.getCurrentMode() == com.advisora.utils.ThemeMode.DARK
                    ? I18n.tr("top.themeLight")
                    : I18n.tr("top.themeNight"));
        }
    }

    private ThemeMode toUiTheme(UserPreferencesManager.ThemeChoice core) {
        if (core == null) {
            return ThemeMode.DARK;
        }
        return switch (core) {
            case LIGHT -> ThemeMode.LIGHT;
            case SYSTEM -> ThemeMode.SYSTEM;
            case DARK -> ThemeMode.DARK;
        };
    }

    private LanguageMode toUiLang(UserPreferencesManager.LanguageChoice core) {
        if (core == null) {
            return LanguageMode.FR;
        }
        return switch (core) {
            case EN -> LanguageMode.EN;
            case FR -> LanguageMode.FR;
        };
    }

    private UserPreferencesManager.ThemeChoice toCoreTheme(ThemeMode ui) {
        if (ui == null) {
            return UserPreferencesManager.ThemeChoice.DARK;
        }
        return switch (ui) {
            case LIGHT -> UserPreferencesManager.ThemeChoice.LIGHT;
            case SYSTEM -> UserPreferencesManager.ThemeChoice.SYSTEM;
            case DARK -> UserPreferencesManager.ThemeChoice.DARK;
        };
    }

    private UserPreferencesManager.LanguageChoice toCoreLang(LanguageMode ui) {
        if (ui == null) {
            return UserPreferencesManager.LanguageChoice.FR;
        }
        return switch (ui) {
            case EN -> UserPreferencesManager.LanguageChoice.EN;
            case FR -> UserPreferencesManager.LanguageChoice.FR;
        };
    }

    private int readFontSizePxFromUi() {
        Integer selected = cmbFontSize.getValue();
        if (selected != null) {
            return normalizeFontSize(selected);
        }
        if (cmbFontSize.getEditor() != null) {
            String raw = cmbFontSize.getEditor().getText();
            if (raw != null && !raw.isBlank()) {
                try {
                    String digits = raw.replaceAll("[^0-9]", "");
                    if (digits.isBlank()) {
                        return 14;
                    }
                    return normalizeFontSize(Integer.parseInt(digits));
                } catch (Exception ignored) {
                    return 14;
                }
            }
        }
        return 14;
    }

    private void commitEditorFontSize() {
        if (cmbFontSize.getEditor() == null) {
            return;
        }
        String raw = cmbFontSize.getEditor().getText();
        if (raw == null || raw.isBlank()) {
            return;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return;
        }
        try {
            int normalized = normalizeFontSize(Integer.parseInt(digits));
            if (!Objects.equals(cmbFontSize.getValue(), normalized)) {
                cmbFontSize.setValue(normalized);
            }
            cmbFontSize.getEditor().setText(String.valueOf(normalized));
            updateFontPreview(normalized);
            if (!initializing) {
                applyLive();
            }
        } catch (Exception ignored) {
            // keep previous valid value
        }
    }

    private int normalizeFontSize(Integer px) {
        int value = px == null ? 14 : px;
        if (value < 10) return 10;
        if (value > 24) return 24;
        return value;
    }
}
