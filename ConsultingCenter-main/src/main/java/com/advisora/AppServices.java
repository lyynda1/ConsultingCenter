package com.advisora;

import com.advisora.Services.TranslationService;

public final class AppServices {
    private AppServices() {}

    public static final TranslationService TRANSLATOR = new TranslationService();
}
