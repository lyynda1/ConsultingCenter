package com.advisora.Services.estimation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FxRateService {
    private static final String BASE_URL = "https://api.frankfurter.app/latest";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public FxRateService() {
        this(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                new ObjectMapper(),
                Clock.systemUTC()
        );
    }

    public FxRateService(HttpClient httpClient, ObjectMapper mapper, Clock clock) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.clock = clock;
    }

    public FxQuote getRate(String fromCurrency, String toCurrency) {
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);
        if (from.equals(to)) {
            return new FxQuote(1.0, LocalDate.now(clock), from, to);
        }

        String key = from + "->" + to;
        CacheEntry cached = cache.get(key);
        if (cached != null && !isExpired(cached.fetchedAt())) {
            return cached.quote();
        }

        FxQuote quote = fetch(from, to);
        cache.put(key, new CacheEntry(quote, Instant.now(clock)));
        return quote;
    }

    private FxQuote fetch(String from, String to) {
        URI uri = URI.create(
                BASE_URL
                        + "?from=" + URLEncoder.encode(from, StandardCharsets.UTF_8)
                        + "&to=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
        );

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
            throw new RuntimeException("FX request interrupted.");
        } catch (IOException e) {
            throw new RuntimeException("FX request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("FX provider returned HTTP " + response.statusCode());
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            JsonNode rates = root.path("rates");
            double rate = rates.path(to).asDouble(Double.NaN);
            String dateStr = root.path("date").asText("");
            if (Double.isNaN(rate) || dateStr.isBlank()) {
                throw new RuntimeException("Invalid FX payload.");
            }
            return new FxQuote(rate, LocalDate.parse(dateStr), from, to);
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse FX payload.", e);
        }
    }

    private String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required.");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isExpired(Instant fetchedAt) {
        return fetchedAt == null || Duration.between(fetchedAt, Instant.now(clock)).compareTo(CACHE_TTL) > 0;
    }

    public record FxQuote(double rate, LocalDate date, String baseCurrency, String targetCurrency) {
    }

    private record CacheEntry(FxQuote quote, Instant fetchedAt) {
    }
}
