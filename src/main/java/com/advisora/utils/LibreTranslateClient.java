package com.advisora.utils;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
public class LibreTranslateClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl; // e.g. http://localhost:5000

    public LibreTranslateClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String translate(String text, String source, String target) throws Exception {
        if (text == null || text.isBlank()) return text;
        if (source.equalsIgnoreCase(target)) return text;

        String body = "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&source=" + URLEncoder.encode(source, StandardCharsets.UTF_8)
                + "&target=" + URLEncoder.encode(target, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/translate"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("LibreTranslate error " + resp.statusCode() + ": " + resp.body());
        }

        // Minimal parse: {"translatedText":"..."}
        String json = resp.body();
        String needle = "\"translatedText\":";
        int i = json.indexOf(needle);
        if (i < 0) return text;

        int firstQuote = json.indexOf('"', i + needle.length());
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote < 0) return text;

        return json.substring(firstQuote + 1, secondQuote)
                .replace("\\n", "\n")
                .replace("\\\"", "\"");
    }
}
