package com.advisora.Services.investment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches live exchange rates from ExchangeRate-API v6.
 * Base currency: TND (Dinar Tunisien â€“ affichÃ© "DNT" dans l'application).
 * Currencies returned: USD, EUR, GBP.
 */
public class ExchangeRateService {

    private static final String API_KEY = "7d694c150f1e0356748d5088";
    private static final String BASE = "TND";
    private static final String BASE_URL = "https://v6.exchangerate-api.com/v6/" + API_KEY + "/latest/" + BASE;

    /** Currencies we want to display (in display order). */
    private static final String[] TARGETS = { "USD", "EUR", "GBP" };

    /**
     * Returns a map of { "USD" -> rate, "EUR" -> rate, "GBP" -> rate }.
     * Rates represent: 1 TND = X [currency].
     *
     * @throws Exception if the network call fails or the API returns an error.
     */
    public Map<String, Double> getRates() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Erreur HTTP " + response.statusCode() + " lors de l'appel Ã  l'API.");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());

        String result = root.path("result").asText();
        if (!"success".equalsIgnoreCase(result)) {
            String errorType = root.path("error-type").asText("unknown");
            throw new Exception("RÃ©ponse API invalide : " + errorType);
        }

        JsonNode conversionRates = root.path("conversion_rates");
        Map<String, Double> rates = new LinkedHashMap<>();
        for (String currency : TARGETS) {
            JsonNode node = conversionRates.path(currency);
            if (!node.isMissingNode()) {
                rates.put(currency, node.asDouble());
            }
        }
        return rates;
    }
}


