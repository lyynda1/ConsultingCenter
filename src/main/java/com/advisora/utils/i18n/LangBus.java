package com.advisora.utils.i18n;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Locale;

public final class LangBus {
        private static final ObjectProperty<Locale> LOCALE = new SimpleObjectProperty<>(Locale.ENGLISH);

        private LangBus() {}

        public static ObjectProperty<Locale> localeProperty() { return LOCALE; }
        public static Locale getLocale() { return LOCALE.get(); }

        public static void setLocale(Locale locale) {
            LOCALE.set(locale == null ? Locale.ENGLISH : locale);
        }}