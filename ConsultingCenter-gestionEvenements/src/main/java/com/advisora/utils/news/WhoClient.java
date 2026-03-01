package com.advisora.utils.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WhoClient {
    private static final ObjectMapper OM = new ObjectMapper();

    public static class WhoResult {
        public int totalCount;     // total items returned by API
        public int recentCount;    // items in last 30 days
        public int count;          // we set this = recentCount (for compatibility)
        public List<String> titles = new ArrayList<>();
        public List<String> urls = new ArrayList<>();
    }

    public WhoResult fetchLatestDiseaseOutbreakNews() throws Exception {

        String url = "https://www.who.int/api/news/diseaseoutbreaknews";
        String json = httpUtil.HttpUtil.get(url);

        JsonNode root = OM.readTree(json);

        JsonNode items = root.path("value");
        if (!items.isArray() && root.isArray()) items = root;

        WhoResult r = new WhoResult();
        r.totalCount = items.isArray() ? items.size() : 0;

        LocalDate cutoff = LocalDate.now().minusDays(30);

        if (items.isArray()) {
            for (int i = 0; i < items.size(); i++) {
                JsonNode it = items.get(i);

                String title = firstNonEmpty(
                        it.path("title").asText(""),
                        it.path("name").asText(""),
                        it.path("headline").asText("")
                );

                String dateStr = firstNonEmpty(
                        it.path("publicationDate").asText(""),
                        it.path("date").asText(""),
                        it.path("published").asText(""),
                        it.path("lastModified").asText("")
                );

                String link = firstNonEmpty(
                        it.path("url").asText(""),
                        it.path("link").asText(""),
                        it.path("href").asText("")
                );

                LocalDate date = parseDateSafe(dateStr);
                if (date == null) continue;
                if (date.isBefore(cutoff)) continue;

                r.recentCount++;

                // store top 5 recent for UI + LLM store
                if (!title.isBlank() && r.titles.size() < 5) {
                    r.titles.add(title + " (" + date + ")");
                    r.urls.add(link == null ? "" : link);
                }
            }
        }

        // ✅ important: keep compatibility with existing aggregator logic
        r.count = r.recentCount;

        return r;
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static LocalDate parseDateSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String iso = s.length() >= 10 ? s.substring(0, 10) : s;
            return LocalDate.parse(iso);
        } catch (Exception ignored) {
            return null;
        }
    }
}