package com.advisora.utils.i18n;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Locale;
import java.util.ResourceBundle;

public final class I18n {

    private static final ObjectProperty<Locale> LOCALE =
            new SimpleObjectProperty<>(Locale.FRENCH);

    private I18n() {}

    public static void setLocale(Locale l) {
        if (l == null) l = Locale.FRENCH;

        Locale finalL = l;
        if (Platform.isFxApplicationThread()) {
            LOCALE.set(finalL);
        } else {
            Platform.runLater(() -> LOCALE.set(finalL));
        }
    }

    public static Locale getLocale() {
        return LOCALE.get();
    }

    public static ObjectProperty<Locale> localeProperty() {
        return LOCALE;
    }

    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle("i18n.messages", getLocale());
    }

    public static String tr(String key) {
        try {
            return bundle().getString(key);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }
}