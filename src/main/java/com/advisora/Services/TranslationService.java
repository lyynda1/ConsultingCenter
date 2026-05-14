package com.advisora.Services;

import com.advisora.utils.LibreTranslateClient;
import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.*;

public class TranslationService {

    private final LibreTranslateClient client;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    // cache key: "fr|en|Bonjour"
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public TranslationService(String baseUrl) {
        this.client = new LibreTranslateClient(baseUrl);
    }

    public String cachedOrSame(String text, String source, String target) {
        if (text == null || text.isBlank()) return text;
        if (source.equalsIgnoreCase(target)) return text;

        String key = source + "|" + target + "|" + text;
        return cache.getOrDefault(key, null);
    }

    public void translateAsync(String text, String source, String target,
                               java.util.function.Consumer<String> onUiResult) {

        if (text == null || text.isBlank()) {
            Platform.runLater(() -> onUiResult.accept(text));
            return;
        }
        if (source.equalsIgnoreCase(target)) {
            Platform.runLater(() -> onUiResult.accept(text));
            return;
        }

        String key = source + "|" + target + "|" + text;
        String hit = cache.get(key);
        if (hit != null) {
            Platform.runLater(() -> onUiResult.accept(hit));
            return;
        }

        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        String tr = client.translate(text, source, target);
                        cache.put(key, tr);
                        return tr;
                    } catch (Exception e) {
                        // fallback: return original text if API fails
                        return text;
                    }
                }, pool)
                .thenAccept(result -> Platform.runLater(() -> onUiResult.accept(result)));
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}