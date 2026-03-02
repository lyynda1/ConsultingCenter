package com.advisora.utils;

import com.advisora.utils.i18n.I18n;

public final class AppLanguage {

    private AppLanguage() {}

    public static java.util.Locale getLocale() {
        return I18n.getLocale();
    }

    // LibreTranslate expects "fr"/"en"/...
    public static String ltTargetCode() {
        return getLocale().getLanguage();
    }
}