package com.advisora.utils.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GdeltClient {
    private static final ObjectMapper OM = new ObjectMapper();

    public static class GdeltResult {
        public int count;
        public List<String> titles = new ArrayList<>();
        public List<String> urls = new ArrayList<>();
    }

    public GdeltResult fetchTunisiaEconomyLast2Days() throws Exception {
        String query = "Tunisia (inflation OR dinar OR IMF OR strike OR shortage OR tax OR subsidy OR currency OR import OR export)";

        String url = "https://api.gdeltproject.org/api/v2/doc/doc"
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&mode=ArtList"
                + "&format=json"
                + "&sort=datedesc"
                + "&maxrecords=50"
                + "&timespan=2d";

        String json = httpUtil.HttpUtil.get(url);
        json = normalizeJson(json);

        // ✅ check BEFORE parsing
        assertLooksLikeJson(json, "GDELT economy");

        JsonNode root = OM.readTree(json);
        JsonNode arts = root.path("articles");

        GdeltResult r = new GdeltResult();
        if (arts.isArray()) {
            r.count = arts.size();

            for (int i = 0; i < arts.size() && i < 10; i++) {
                String title = arts.get(i).path("title").asText("");
                String link  = arts.get(i).path("url").asText("");

                if (!title.isBlank()) {
                    r.titles.add(title);
                    r.urls.add(link == null ? "" : link);
                }

                if (r.titles.size() >= 5) break;
            }
        } else {
            r.count = 0;
        }
        return r;
    }

    public List<String> searchRelatedToStrategyTitle(String strategyTitle) throws Exception {
        String kw = cleanKeywords(strategyTitle);
        if (kw.length() < 4) kw = "economy";

        String query = "(" + kw + ") (Tunisia OR Tunis)";

        String url = "https://api.gdeltproject.org/api/v2/doc/doc"
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&mode=ArtList"
                + "&format=json"
                + "&sort=datedesc"
                + "&maxrecords=10"
                + "&timespan=7d";

        String json = httpUtil.HttpUtil.get(url);
        json = normalizeJson(json);

        // ✅ check BEFORE parsing
        assertLooksLikeJson(json, "GDELT related");

        JsonNode root = OM.readTree(json);

        List<String> out = new ArrayList<>();
        JsonNode arts = root.path("articles");
        if (arts.isArray()) {
            for (int i = 0; i < arts.size() && i < 5; i++) {
                String title = arts.get(i).path("title").asText("");
                String link  = arts.get(i).path("url").asText("");
                if (!title.isBlank()) out.add(title + (link.isBlank() ? "" : (" — " + link)));
            }
        }
        return out;
    }

    // ---------------- Helpers ----------------

    private static void assertLooksLikeJson(String raw, String label) {
        if (raw == null) throw new RuntimeException(label + " returned null");

        String s = raw.trim();

        // unwrap JSONP again just in case
        if (s.startsWith("(") && s.endsWith(")")) s = s.substring(1, s.length() - 1).trim();
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();

        // MUST start with { or [
        if (!(s.startsWith("{") || s.startsWith("["))) {
            String snippet = s.length() > 250 ? s.substring(0, 250) : s;
            throw new RuntimeException(label + " returned NON-JSON. Starts with: " + snippet);
        }

        // quick sanity: JSON object must have quoted keys, not C, not plain text
        // (this is optional, but helps detect weird responses)
        if (s.startsWith("{") && s.length() >= 2) {
            char c = s.charAt(1);
            if (c != '"' && c != '}' && !Character.isWhitespace(c)) {
                String snippet = s.length() > 250 ? s.substring(0, 250) : s;
                throw new RuntimeException(label + " returned INVALID JSON object. Starts with: " + snippet);
            }
        }
    }

    private static String cleanKeywords(String strategyTitle) {
        if (strategyTitle == null) return "";
        String kw = strategyTitle.replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim();
        kw = kw.replaceAll("\\s+", " ");

        // ✅ keep it short (avoid massive query)
        if (kw.length() > 80) kw = kw.substring(0, 80).trim();
        return kw;
    }

    private static String normalizeJson(String body) {
        if (body == null) return "";

        String s = body.trim();

        // Remove BOM if present
        if (s.startsWith("\uFEFF")) s = s.substring(1).trim();

        // JSONP: callbackName( ... );
        int firstBrace = s.indexOf('{');
        int firstBracket = s.indexOf('[');

        // If it doesn't start with { or [, try to cut from first { or [
        if (!(s.startsWith("{") || s.startsWith("["))) {
            int start = -1;
            if (firstBrace >= 0 && firstBracket >= 0) start = Math.min(firstBrace, firstBracket);
            else start = Math.max(firstBrace, firstBracket);
            if (start > 0) s = s.substring(start).trim();
        }

        // Remove trailing stuff after last } or ]
        int lastBrace = s.lastIndexOf('}');
        int lastBracket = s.lastIndexOf(']');
        int end = Math.max(lastBrace, lastBracket);
        if (end > 0 && end < s.length() - 1) {
            s = s.substring(0, end + 1).trim();
        }

        // Unwrap ( ... ) again
        if (s.startsWith("(") && s.endsWith(")")) s = s.substring(1, s.length() - 1).trim();
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();

        return s;
    }}