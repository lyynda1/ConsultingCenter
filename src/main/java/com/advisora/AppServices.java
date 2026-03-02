package com.advisora;

import com.advisora.Services.TranslationService;

public final class AppServices {
    private AppServices() {}

    // LibreTranslate running locally
    public static final TranslationService TRANSLATOR =
            new TranslationService("http://localhost:5000");
}