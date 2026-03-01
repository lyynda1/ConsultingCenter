package com.advisora.Services.strategie;

import com.advisora.Model.strategie.ExternalEvent;
import com.advisora.Model.strategie.NewsItem;
import com.advisora.enums.Severity;
import com.advisora.utils.news.NewsJsonStore;
import com.advisora.utils.news.GdeltClient;
import com.advisora.utils.news.WhoClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RiskScheduler {

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    private final ExternalEventStore store;
    private final GdeltClient gdelt = new GdeltClient();
    private final WhoClient who = new WhoClient();
    private final EventAggregator agg = new EventAggregator();

    // âœ… News JSON store (auto-creates data/news_items.json)
    private final NewsJsonStore newsStore = new NewsJsonStore(Path.of("data", "news_items.json"));

    public RiskScheduler(ExternalEventStore store) {
        this.store = store;
    }

    public void start() {
        exec.scheduleAtFixedRate(() -> {
            System.out.println("â³ RiskScheduler tick...");

            // Load existing news
            List<NewsItem> all = new ArrayList<>(newsStore.readAll());

            // Avoid duplicates by URL (fallback to title)
            Set<String> seen = new HashSet<>();
            for (NewsItem n : all) {
                String key = keyOf(n);
                if (!key.isBlank()) seen.add(key);
            }

            // ---------------- GDELT (ECONOMY) ----------------
            try {
                var g = gdelt.fetchTunisiaEconomyLast2Days();
                System.out.println("âœ… GDELT OK, count=" + g.count);

                // Save macro ECONOMY event
                store.upsert(agg.buildEconomyEventFromGdelt(g));
                System.out.println("âœ… Saved ECONOMY event to events.json");

                // Save top news items from GDELT
                for (int i = 0; i < g.titles.size(); i++) {
                    String title = g.titles.get(i);
                    String url = (g.urls != null && i < g.urls.size()) ? g.urls.get(i) : "";

                    NewsItem ni = new NewsItem(title, url, "GDELT", null);
                    String key = keyOf(ni);

                    if (!key.isBlank() && !seen.contains(key)) {
                        all.add(ni);
                        seen.add(key);
                    }
                }

            } catch (Exception ex) {
                System.out.println("âŒ GDELT FAILED: " + ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? (" - " + ex.getMessage()) : ""));

                // âœ… VERY IMPORTANT:
                // Deactivate ECONOMY event so stale CRITICAL doesn't keep blocking users.
                try {
                    ExternalEvent e = new ExternalEvent();
                    e.setSource("GDELT");
                    e.setEventType("ECONOMY");
                    e.setEventName("Tunisia economy risk (last 48h)");
                    e.setCurrentValue(0.0);
                    e.setUnit("articles/48h");
                    e.setSeverity(Severity.INFO);
                    e.setDescription("DonnÃ©es Ã©conomiques indisponibles (GDELT). DerniÃ¨re tentative Ã©chouÃ©e.");
                    e.setActive(false);

                    store.upsert(e);
                    System.out.println("âš ï¸ ECONOMY event deactivated (GDELT unavailable)");
                } catch (Exception ex2) {
                    System.out.println("âŒ Could not deactivate ECONOMY event: "
                            + ex2.getClass().getSimpleName()
                            + (ex2.getMessage() != null ? (" - " + ex2.getMessage()) : ""));
                }
            }

            // ---------------- WHO (HEALTH) ----------------
            try {
                var w = who.fetchLatestDiseaseOutbreakNews();
                System.out.println("âœ… WHO OK, recent=" + w.recentCount + ", total=" + w.totalCount);

                // Save macro HEALTH event
                store.upsert(agg.buildHealthEventFromWho(w));
                System.out.println("âœ… Saved HEALTH event to events.json");

                // Save WHO news items
                for (int i = 0; i < w.titles.size(); i++) {
                    String title = w.titles.get(i);
                    String url = (w.urls != null && i < w.urls.size()) ? w.urls.get(i) : "";

                    NewsItem ni = new NewsItem(title, url, "WHO", null);
                    String key = keyOf(ni);

                    if (!key.isBlank() && !seen.contains(key)) {
                        all.add(ni);
                        seen.add(key);
                    }
                }

            } catch (Exception ex) {
                System.out.println("âŒ WHO FAILED: " + ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? (" - " + ex.getMessage()) : ""));
            }

            // Keep only last 150 items
            if (all.size() > 150) {
                all = all.subList(all.size() - 150, all.size());
            }

            // Write news JSON
            newsStore.writeAll(all);
            System.out.println("âœ… Saved news_items.json size=" + all.size());

        }, 0, 60, TimeUnit.MINUTES);
    }

    public void stop() {
        exec.shutdownNow();
    }

    private static String keyOf(NewsItem n) {
        if (n == null) return "";
        if (n.url != null && !n.url.isBlank()) return n.url.trim();
        if (n.title != null && !n.title.isBlank()) return ("TITLE:" + n.title.trim());
        return "";
    }
}
