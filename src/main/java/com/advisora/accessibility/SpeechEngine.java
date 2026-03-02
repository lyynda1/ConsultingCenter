package com.advisora.accessibility;

public interface SpeechEngine {
    boolean isAvailable();
    void speakAsync(String text);
    void stop();
}
