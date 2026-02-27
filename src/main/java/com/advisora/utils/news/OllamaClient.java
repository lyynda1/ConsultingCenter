package com.advisora.utils.news;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaClient {
    private static final ObjectMapper OM = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;
    private final String model;

    // Example model: "gemini-1.5-flash"
    public OllamaClient(String apiKey, String model) {
        this.apiKey = "AIzaSyDrXYk97DUPKfGHWCbRwi6-9Y4KlgDTLvo";
        this.model = model;
    }



    public String generate(String prompt) throws Exception {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    + "?key=" + apiKey;

        String payload = """
        {
          "contents": [
            {
              "parts": [
                { "text": %s }
              ]
            }
          ]
        }
        """.formatted(OM.writeValueAsString(prompt));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("Gemini HTTP " + res.statusCode() + ": " + res.body());
        }

        JsonNode root = OM.readTree(res.body());

        return root
                .path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText("");
    }
}