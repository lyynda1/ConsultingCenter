package com.advisora.Services.strategie;

import com.advisora.utils.news.OllamaClient;

import java.nio.file.Path;

public final class RiskContext {

    private static RiskScheduler scheduler;
    private static RiskService riskService;

    private RiskContext() {}

    public static void init() {
        // store file: ./data/events.json
        Path p = Path.of("data", "events.json").toAbsolutePath();
        System.out.println("âœ… events.json path = " + p);
        ExternalEventStore store = new JsonEventStore(p);

        scheduler = new RiskScheduler(store);
        scheduler.start();

        OllamaClient llm = new OllamaClient(System.getenv("AIzaSyDrXYk97DUPKfGHWCbRwi6-9Y4KlgDTLvo"), "gemini-1.5-flash");
        riskService = new RiskService(store, llm);
    }

    public static RiskService getRiskService() {
        if (riskService == null) {
            throw new IllegalStateException("RiskContext not initialized. Call RiskContext.init() in App.start().");
        }
        return riskService;
    }

    public static void shutdown() {
        if (scheduler != null) scheduler.stop();
    }
}
