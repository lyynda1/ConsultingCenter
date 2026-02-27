package com.advisora.Services.projet;

import com.advisora.Model.projet.Top10CompanyItem;
import com.advisora.Model.projet.Top10Response;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Year;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Supplier;

public class Top10AiService {
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final String DEFAULT_DISCLAIMER = "Classement indicatif - chiffres estimatifs, non contractuels.";
    private static final Path CACHE_FILE = Path.of("uploads", "top10-cache.json");
    private static final Object CACHE_LOCK = new Object();

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Supplier<String> apiKeySupplier;

    public Top10AiService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                new ObjectMapper(),
                () -> System.getenv("GEMINI_API_KEY"));
    }

    Top10AiService(HttpClient httpClient, ObjectMapper mapper, Supplier<String> apiKeySupplier) {
        this.httpClient = httpClient;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.apiKeySupplier = apiKeySupplier;
    }

    public Top10Response generateTop10(String userInputCategory) {
        String category = sanitizeCategory(userInputCategory);
        String cacheKey = toCacheKey(category);

        Top10Response cached = readFromCache(cacheKey);
        if (cached != null) {
            try {
                logTop10Source("CACHE FILE", category, cacheKey);
                return normalizeAndValidate(cached);
            } catch (Exception ex) {
                System.out.println("[Top10] cache entry invalid for key=" + cacheKey + ", fallback API.");
            }
        }

        logTop10Source("API GEMINI", category, cacheKey);
        String apiKey = safeApiKey();

        HttpResponse<String> response;
        try {
            String requestBody = buildRequestBody(category);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL))
                    .timeout(TIMEOUT)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(mapApiError(response.statusCode(), response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Service indisponible, reessayez.");
        } catch (IOException e) {
            throw new IllegalStateException("Service indisponible, reessayez.");
        }

        try {
            String assistantJson = extractAssistantJson(response.body());
            Top10Response parsed = parseAssistantJson(assistantJson);
            Top10Response normalized = normalizeAndValidate(parsed);
            writeToCache(cacheKey, normalized);
            return normalized;
        } catch (IOException e) {
            throw new IllegalStateException("Reponse invalide, reessayez.");
        }
    }

    String extractAssistantJson(String apiResponseJson) throws IOException {
        JsonNode root = mapper.readTree(apiResponseJson);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Reponse invalide, reessayez.");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Reponse vide, reessayez.");
        }

        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                return text;
            }
        }

        throw new IllegalStateException("Reponse vide, reessayez.");
    }

    Top10Response parseAssistantJson(String assistantJson) throws IOException {
        String clean = assistantJson == null ? "" : assistantJson.trim();
        if (clean.startsWith("```")) {
            clean = clean.replaceFirst("^```(?:json)?\\s*", "");
            clean = clean.replaceFirst("\\s*```$", "");
        }
        return mapper.readValue(clean, Top10Response.class);
    }

    Top10Response normalizeAndValidate(Top10Response input) {
        if (input == null) {
            throw new IllegalStateException("Reponse invalide, reessayez.");
        }

        List<Top10CompanyItem> rows = input.getTop10();
        if (rows == null || rows.size() != 10) {
            throw new IllegalStateException("Reponse incomplete: Top 10 requis.");
        }

        int maxYear = Year.now().getValue() + 1;
        boolean[] seenRanks = new boolean[11];
        for (Top10CompanyItem row : rows) {
            if (row == null) {
                throw new IllegalStateException("Reponse invalide, reessayez.");
            }
            if (row.getRank() < 1 || row.getRank() > 10) {
                throw new IllegalStateException("Reponse invalide: rang incorrect.");
            }
            if (seenRanks[row.getRank()]) {
                throw new IllegalStateException("Reponse invalide: rang duplique.");
            }
            seenRanks[row.getRank()] = true;

            if (row.getName() == null || row.getName().isBlank()) {
                throw new IllegalStateException("Reponse invalide: nom manquant.");
            }
            if (Double.isNaN(row.getRevenueUsdBillions())
                    || Double.isInfinite(row.getRevenueUsdBillions())
                    || row.getRevenueUsdBillions() < 0.0) {
                throw new IllegalStateException("Reponse invalide: revenu incorrect.");
            }
            if (row.getYear() < 1990 || row.getYear() > maxYear) {
                throw new IllegalStateException("Reponse invalide: annee incorrecte.");
            }
            if (row.getDescription() == null || row.getDescription().isBlank()) {
                throw new IllegalStateException("Reponse invalide: description manquante.");
            }
        }

        rows.sort(Comparator.comparingDouble(Top10CompanyItem::getRevenueUsdBillions).reversed());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }

        if (input.getCategory() == null || input.getCategory().isBlank()) {
            input.setCategory("Categorie non precisee");
        }
        input.setDisclaimer(DEFAULT_DISCLAIMER);
        return input;
    }

    private String sanitizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Veuillez saisir un secteur.");
        }
        return raw.trim();
    }

    private String safeApiKey() {
        String key = apiKeySupplier == null ? null : apiKeySupplier.get();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Configuration manquante: GEMINI_API_KEY.");
        }
        return key.trim();
    }

    private String mapApiError(int statusCode, String responseBody) {
        String status = "";
        String message = "";
        try {
            JsonNode root = mapper.readTree(responseBody == null ? "" : responseBody);
            JsonNode error = root.path("error");
            status = error.path("status").asText("");
            message = error.path("message").asText("");
        } catch (Exception ignored) {
            // Keep generic fallback below.
        }

        if (statusCode == 429) {
            if (message.toLowerCase().contains("quota") || "RESOURCE_EXHAUSTED".equalsIgnoreCase(status)) {
                return "Quota Gemini insuffisant. Verifiez facturation/credits puis reessayez.";
            }
            return "Limite de requetes Gemini atteinte. Reessayez dans quelques secondes.";
        }
        if (statusCode == 401) {
            return "Cle API Gemini invalide. Verifiez GEMINI_API_KEY.";
        }
        if (statusCode == 403) {
            return "Acces Gemini refuse pour ce projet/cle.";
        }

        if (message == null || message.isBlank()) {
            return "Service indisponible (code " + statusCode + "). Reessayez.";
        }
        return "Service indisponible (code " + statusCode + "): " + message;
    }

    private String toCacheKey(String category) {
        return canonicalize(category);
    }

    private String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compactKey(String text) {
        return text == null ? "" : text.replace(" ", "");
    }

    private Top10Response readFromCache(String cacheKey) {
        synchronized (CACHE_LOCK) {
            CacheSnapshot snapshot = loadCacheSnapshot();
            if (snapshot.fileExists() && !snapshot.parsedOk()) {
                System.out.println("[Top10] cache file unreadable, fallback API. file=" + CACHE_FILE);
                return null;
            }
            Top10Response cached = snapshot.cache().get(cacheKey);
            if (cached == null) {
                String wanted = canonicalize(cacheKey);
                String wantedCompact = compactKey(wanted);
                for (Map.Entry<String, Top10Response> entry : snapshot.cache().entrySet()) {
                    String existing = canonicalize(entry.getKey());
                    if (existing.equals(wanted) || compactKey(existing).equals(wantedCompact)) {
                        cached = entry.getValue();
                        break;
                    }
                }
            }
            if (cached == null) {
                return null;
            }
            Top10Response copy = mapper.convertValue(cached, Top10Response.class);
            if (copy.getDisclaimer() == null || copy.getDisclaimer().isBlank()) {
                copy.setDisclaimer(DEFAULT_DISCLAIMER);
            }
            return copy;
        }
    }

    private void writeToCache(String cacheKey, Top10Response response) {
        synchronized (CACHE_LOCK) {
            CacheSnapshot snapshot = loadCacheSnapshot();
            if (snapshot.fileExists() && !snapshot.parsedOk()) {
                // Existing file cannot be parsed: never overwrite user data.
                return;
            }
            Map<String, Top10Response> cache = new HashMap<>(snapshot.cache());
            Top10Response copy = mapper.convertValue(response, Top10Response.class);
            cache.put(cacheKey, copy);
            saveCacheMap(cache);
        }
    }

    private CacheSnapshot loadCacheSnapshot() {
        boolean exists = Files.exists(CACHE_FILE);
        try {
            if (!exists) {
                return new CacheSnapshot(new HashMap<>(), false, true);
            }
            String json = Files.readString(CACHE_FILE);
            if (json == null || json.isBlank()) {
                return new CacheSnapshot(new HashMap<>(), true, true);
            }
            Map<String, Top10Response> parsed = mapper.readValue(
                    json,
                    new TypeReference<Map<String, Top10Response>>() {}
            );
            Map<String, Top10Response> safe = parsed == null ? new HashMap<>() : new HashMap<>(parsed);
            return new CacheSnapshot(safe, true, true);
        } catch (Exception ignored) {
            return new CacheSnapshot(new HashMap<>(), exists, false);
        }
    }

    private void saveCacheMap(Map<String, Top10Response> cache) {
        try {
            if (CACHE_FILE.getParent() != null) {
                Files.createDirectories(CACHE_FILE.getParent());
            }
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cache);
            Path tempFile = CACHE_FILE.resolveSibling(CACHE_FILE.getFileName() + ".tmp");
            Files.writeString(tempFile, json);
            Files.move(tempFile, CACHE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            // Cache write failure should not break the feature.
        }
    }

    private void logTop10Source(String source, String category, String cacheKey) {
        System.out.println("[Top10] source=" + source + " | category=\"" + category + "\" | key=" + cacheKey);
    }

    private record CacheSnapshot(Map<String, Top10Response> cache, boolean fileExists, boolean parsedOk) {
    }

    private String buildRequestBody(String category) throws IOException {
        ObjectNode root = mapper.createObjectNode();

        ObjectNode systemInstruction = root.putObject("systemInstruction");
        ArrayNode systemParts = systemInstruction.putArray("parts");
        systemParts.addObject().put("text",
                "Tu es un assistant data. Reponds uniquement avec un JSON valide selon le schema fourni.");

        ArrayNode contents = root.putArray("contents");
        ObjectNode user = contents.addObject();
        user.put("role", "user");
        ArrayNode userParts = user.putArray("parts");
        userParts.addObject().put("text",
                "Secteur: " + category + ". " +
                "Interprete FR/EN et synonymes. Retourne exactement 10 elements tries par revenu decroissant, " +
                "avec description simple d'une phrase.");

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("temperature", 0.2);
        generationConfig.put("responseMimeType", "application/json");

        ObjectNode schema = generationConfig.putObject("responseJsonSchema");
        schema.put("type", "object");

        ArrayNode requiredRoot = schema.putArray("required");
        requiredRoot.add("category");
        requiredRoot.add("top10");
        requiredRoot.add("disclaimer");

        ObjectNode props = schema.putObject("properties");
        props.putObject("category").put("type", "string");
        props.putObject("disclaimer").put("type", "string");

        ObjectNode top10 = props.putObject("top10");
        top10.put("type", "array");
        top10.put("minItems", 10);
        top10.put("maxItems", 10);

        ObjectNode item = top10.putObject("items");
        item.put("type", "object");

        ArrayNode requiredItem = item.putArray("required");
        requiredItem.add("rank");
        requiredItem.add("name");
        requiredItem.add("revenue_usd_billions");
        requiredItem.add("year");
        requiredItem.add("description");

        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("rank").put("type", "integer");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("revenue_usd_billions").put("type", "number");
        itemProps.putObject("year").put("type", "integer");
        itemProps.putObject("description").put("type", "string");

        return mapper.writeValueAsString(root);
    }
}
