package com.advisora.Services.projet;

import com.advisora.Model.projet.NewsArticle;
import com.advisora.Model.projet.Project;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class NewsService {
    private static final String API_URL = "https://gnews.io/api/v4/search";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ARTICLES = 20;
    private static final String DEFAULT_GNEWS_API_KEY = "f940f678f40b02b7ec50507bdfc4ccb9";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Supplier<String> apiKeySupplier;
    private final Clock clock;
    private final Map<Integer, CacheEntry> cache = new ConcurrentHashMap<>();

    public NewsService() {
        this(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                new ObjectMapper(),
                NewsService::resolveApiKeyFromRuntime,
                Clock.systemUTC()
        );
    }

    NewsService(HttpClient httpClient, ObjectMapper mapper, Supplier<String> apiKeySupplier, Clock clock) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.apiKeySupplier = apiKeySupplier;
        this.clock = clock;
    }

    public List<NewsArticle> fetchNews(Project project) {
        return fetchNews(project, false);
    }

    public List<NewsArticle> fetchNews(Project project, boolean forceRefresh) {
        if (project == null || project.getIdProj() <= 0) {
            throw new NewsServiceException(NewsErrorType.CONFIG, "Invalid project.");
        }

        String apiKey = apiKey();
        if (!forceRefresh) {
            CacheEntry cached = cache.get(project.getIdProj());
            if (cached != null && !isExpired(cached.fetchedAt())) {
                return cached.articles();
            }
        }

        List<QueryParts> candidates = buildQueryCandidates(project);
        for (QueryParts query : candidates) {
            HttpResponse<String> response = executeRequest(query, apiKey);
            List<NewsArticle> articles = parseArticles(response.body(), project, query.category());
            if (!articles.isEmpty()) {
                cache.put(project.getIdProj(), new CacheEntry(List.copyOf(articles), now()));
                return articles;
            }
        }
        throw new NewsServiceException(NewsErrorType.NO_RESULTS, "No related news found for this project/category.");
    }

    public void invalidateCache(int projectId) {
        cache.remove(projectId);
    }

    QueryParts buildQuery(Project project) {
        String normalizedType = normalize(project.getTypeProj());
        String projectTitle = safe(project.getTitleProj());
        Mapping mapping = mappingForType(normalizedType);

        String keywords = (projectTitle + " " + mapping.mappedTerms()).trim();
        return new QueryParts(mapping.apiCategory(), keywords.replaceAll("\\s+", " "));
    }

    private List<QueryParts> buildQueryCandidates(Project project) {
        QueryParts primary = buildQuery(project);
        Mapping mapping = mappingForType(normalize(project.getTypeProj()));
        String title = safe(project.getTitleProj());

        LinkedHashSet<String> keywordCandidates = new LinkedHashSet<>();
        keywordCandidates.add(primary.keywords());
        if (!mapping.mappedTerms().isBlank()) {
            keywordCandidates.add(mapping.mappedTerms());
        }
        if (!title.isBlank()) {
            keywordCandidates.add(title);
        }
        keywordCandidates.add(genericTermsForCategory(mapping.apiCategory()));

        List<QueryParts> out = new ArrayList<>();
        for (String keywords : keywordCandidates) {
            String clean = safe(keywords).replaceAll("\\s+", " ").trim();
            if (!clean.isBlank()) {
                out.add(new QueryParts(mapping.apiCategory(), clean));
            }
        }
        return out;
    }

    Mapping mappingForType(String normalizedType) {
        if (containsAny(normalizedType, "fintech", "finance", "bank", "payment", "paiement")) {
            return new Mapping("business", "fintech banking payments");
        }
        if (containsAny(normalizedType, "it", "tech", "software", "saas", "digital", "informatique")) {
            return new Mapping("technology", "technology software it");
        }
        if (containsAny(normalizedType, "health", "healthcare", "medical", "med", "sante")) {
            return new Mapping("health", "healthcare medical");
        }
        if (containsAny(normalizedType, "energy", "oil", "solar", "gas", "renewable", "energie")) {
            return new Mapping("business", "energy oil solar");
        }
        if (containsAny(normalizedType, "realestate", "real estate", "housing", "property", "immobilier")) {
            return new Mapping("business", "real estate housing");
        }
        return new Mapping("business", "");
    }

    private List<NewsArticle> parseArticles(String rawBody, Project project, String category) {
        try {
            JsonNode root = mapper.readTree(rawBody == null ? "" : rawBody);
            JsonNode articles = root.path("articles");
            if (!articles.isArray()) {
                return List.of();
            }

            List<NewsArticle> out = new ArrayList<>();
            for (JsonNode item : articles) {
                if (out.size() >= MAX_ARTICLES) {
                    break;
                }
                String title = item.path("title").asText("");
                String description = item.path("description").asText("");
                String url = item.path("url").asText("");
                String source = sourceFrom(item);
                String publishedAt = publishedFrom(item);

                out.add(new NewsArticle(
                        title,
                        description,
                        url,
                        source,
                        publishedAt,
                        category,
                        project.getIdProj(),
                        project.getTitleProj()
                ));
            }
            return out;
        } catch (IOException e) {
            throw new NewsServiceException(NewsErrorType.SERVER, "Malformed response from News API.", e);
        }
    }

    private String sourceFrom(JsonNode item) {
        String source = item.path("source").path("name").asText("");
        if (source.isBlank()) {
            source = item.path("author").asText("");
        }
        return source;
    }

    private String publishedFrom(JsonNode item) {
        String published = item.path("publishedAt").asText("");
        if (published.isBlank()) {
            published = item.path("published").asText("");
        }
        return published;
    }

    private URI buildUri(String category, String keywords, String apiKey) {
        String encodedKeywords = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
        String encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8);
        String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(API_URL
                + "?q=" + encodedKeywords
                + "&category=" + encodedCategory
                + "&lang=en"
                + "&apikey=" + encodedApiKey);
    }

    private HttpResponse<String> executeRequest(QueryParts query, String apiKey) {
        URI uri = buildUri(query.category(), query.keywords(), apiKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NewsServiceException(NewsErrorType.NETWORK, "News request interrupted.");
        } catch (IOException e) {
            throw new NewsServiceException(
                    NewsErrorType.NETWORK,
                    "Network error while loading news. Check connection and retry.",
                    e
            );
        }

        int code = response.statusCode();
        if (code == 429) {
            throw new NewsServiceException(NewsErrorType.RATE_LIMIT, "GNews API rate limit reached. Please try again shortly.");
        }
        if (code == 401 || code == 403) {
            throw new NewsServiceException(NewsErrorType.UNAUTHORIZED, "Unauthorized GNews API request. Check GNEWS_API_KEY.");
        }
        if (code < 200 || code >= 300) {
            throw new NewsServiceException(NewsErrorType.SERVER, "GNews service unavailable (HTTP " + code + ").");
        }
        return response;
    }

    private String genericTermsForCategory(String category) {
        if ("technology".equalsIgnoreCase(category)) {
            return "technology software ai cybersecurity cloud";
        }
        if ("health".equalsIgnoreCase(category)) {
            return "healthcare medical biotech hospital";
        }
        return "business market economy companies";
    }

    private boolean isExpired(Instant fetchedAt) {
        return fetchedAt == null || Duration.between(fetchedAt, now()).compareTo(CACHE_TTL) > 0;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private String apiKey() {
        String key = apiKeySupplier == null ? null : apiKeySupplier.get();
        String cleaned = sanitizeSecret(key);
        if (cleaned == null || cleaned.isBlank()) {
            cleaned = DEFAULT_GNEWS_API_KEY;
        }
        return cleaned;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsAny(String text, String... probes) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String compact = text.replace(" ", "");
        for (String probe : probes) {
            if (probe == null || probe.isBlank()) {
                continue;
            }
            String normalizedProbe = normalize(probe);
            if (normalizedProbe.isBlank()) {
                continue;
            }
            if (text.contains(normalizedProbe) || compact.contains(normalizedProbe.replace(" ", ""))) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String resolveApiKeyFromRuntime() {
        String fromEnv = System.getenv("GNEWS_API_KEY");
        if (!isBlank(fromEnv)) {
            return fromEnv;
        }
        String fromJvmProperty = System.getProperty("GNEWS_API_KEY");
        if (!isBlank(fromJvmProperty)) {
            return fromJvmProperty;
        }
        String fromJvmPropertyLower = System.getProperty("gnews.api.key");
        if (!isBlank(fromJvmPropertyLower)) {
            return fromJvmPropertyLower;
        }
        return readWindowsUserEnvVar("GNEWS_API_KEY");
    }

    private static String sanitizeSecret(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String readWindowsUserEnvVar(String variableName) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return null;
        }
        Process process = null;
        try {
            process = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", variableName)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.toLowerCase(Locale.ROOT).startsWith(variableName.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    String[] parts = trimmed.split("\\s{2,}");
                    if (parts.length >= 3) {
                        return sanitizeSecret(parts[2]);
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    public enum NewsErrorType {
        NO_RESULTS,
        RATE_LIMIT,
        NETWORK,
        UNAUTHORIZED,
        SERVER,
        CONFIG
    }

    public static class NewsServiceException extends RuntimeException {
        private final NewsErrorType type;

        public NewsServiceException(NewsErrorType type, String message) {
            super(message);
            this.type = type;
        }

        public NewsServiceException(NewsErrorType type, String message, Throwable cause) {
            super(message, cause);
            this.type = type;
        }

        public NewsErrorType getType() {
            return type;
        }
    }

    record QueryParts(String category, String keywords) {
    }

    record Mapping(String apiCategory, String mappedTerms) {
    }

    private record CacheEntry(List<NewsArticle> articles, Instant fetchedAt) {
    }
}

