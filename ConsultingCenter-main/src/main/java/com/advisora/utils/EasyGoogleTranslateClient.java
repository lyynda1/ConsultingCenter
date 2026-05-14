package com.advisora.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class EasyGoogleTranslateClient {

    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String translate(String text, String source, String target) throws Exception {
        if (text == null || text.isBlank()) return text;
        if (source.equalsIgnoreCase(target)) return text;

        String query = "client=gtx"
                + "&sl=" + URLEncoder.encode(source, StandardCharsets.UTF_8)
                + "&tl=" + URLEncoder.encode(target, StandardCharsets.UTF_8)
                + "&dt=t"
                + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "?" + query))
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("EasyGoogleTranslate error " + resp.statusCode() + ": " + resp.body());
        }

        return extractTranslatedText(resp.body(), text);
    }

    private String extractTranslatedText(String rawJson, String fallback) {
        try {
            JsonNode root = mapper.readTree(rawJson);
            if (!root.isArray() || root.isEmpty()) return fallback;

            JsonNode segments = root.get(0);
            if (!segments.isArray()) return fallback;

            StringBuilder out = new StringBuilder();
            for (JsonNode segment : segments) {
                if (segment.isArray() && segment.size() > 0 && segment.get(0).isTextual()) {
                    out.append(segment.get(0).asText());
                }
            }
            return out.isEmpty() ? fallback : out.toString();
        } catch (Exception ignore) {
            return fallback;
        }
    }
}
