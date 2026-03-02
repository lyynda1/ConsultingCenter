package com.advisora.Services.investment;

import com.advisora.Model.investment.MacroIndicators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Fetches macroeconomic indicators from the World Bank public API (no key
 * required).
 * Country: TN (Tunisia)
 *
 * NOTE: World Bank does NOT publish TN lending/deposit rates (FR.INR.LEND /
 * FR.INR.DPST).
 * We fall back to the BCT (Banque Centrale de Tunisie) key rate = 8.0% (2024)
 * and flag it as estimated in the returned MacroIndicators.
 */
public class WorldBankService {

    // per_page=10 and mrv=10 to maximise chances of finding a non-null data point
    private static final String BASE = "https://api.worldbank.org/v2/country/TN/indicator/%s?format=json&mrv=10&per_page=10";

    private static final String INFLATION = "FP.CPI.TOTL.ZG";
    private static final String LENDING = "FR.INR.LEND";
    private static final String GDP_GROWTH = "NY.GDP.MKTP.KD.ZG";

    /**
     * BCT key rate (taux directeur) used when World Bank has no TN lending data.
     */
    private static final double BCT_FALLBACK_RATE = 8.0;
    private static final int BCT_FALLBACK_YEAR = 2024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Fetches inflation + GDP growth concurrently; uses BCT fallback for lending
     * rate.
     *
     * @throws Exception on network or JSON failure for a required indicator
     */
    public MacroIndicators fetchIndicators() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<double[]> futureInfl = pool.submit(() -> fetchLatest(INFLATION));
            Future<double[]> futureLend = pool.submit(() -> tryFetchLatest(LENDING,
                    new double[] { BCT_FALLBACK_RATE, BCT_FALLBACK_YEAR }));
            Future<double[]> futureGdp = pool.submit(() -> fetchLatest(GDP_GROWTH));

            double[] infl = futureInfl.get(20, TimeUnit.SECONDS);
            double[] lend = futureLend.get(20, TimeUnit.SECONDS);
            double[] gdp = futureGdp.get(20, TimeUnit.SECONDS);

            // Whether lending came from World Bank or BCT fallback
            boolean lendingEstimated = (lend[1] == BCT_FALLBACK_YEAR
                    && lend[0] == BCT_FALLBACK_RATE);

            // Use the most recent year that is common to all live indicators
            int year = (int) Math.min(infl[1], gdp[1]);

            return new MacroIndicators(infl[0], lend[0], gdp[0], year, lendingEstimated);

        } finally {
            pool.shutdown();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Fetches the most recent non-null value. Throws if none found.
     * Returns [value, year].
     */
    private double[] fetchLatest(String indicator) throws Exception {
        JsonNode dataArray = fetchDataArray(indicator);

        for (JsonNode entry : dataArray) {
            JsonNode valNode = entry.path("value");
            if (!valNode.isNull() && !valNode.isMissingNode()) {
                return new double[] { valNode.asDouble(), entry.path("date").asInt() };
            }
        }
        throw new Exception("Aucune donnée disponible pour l'indicateur " + indicator);
    }

    /**
     * Like fetchLatest but returns {@code fallback} instead of throwing when no
     * data exists.
     */
    private double[] tryFetchLatest(String indicator, double[] fallback) {
        try {
            JsonNode dataArray = fetchDataArray(indicator);
            for (JsonNode entry : dataArray) {
                JsonNode valNode = entry.path("value");
                if (!valNode.isNull() && !valNode.isMissingNode()) {
                    return new double[] { valNode.asDouble(), entry.path("date").asInt() };
                }
            }
        } catch (Exception ignored) {
            /* fall through to fallback */ }
        return fallback;
    }

    /** Returns the data array (element [1]) from a World Bank JSON response. */
    private JsonNode fetchDataArray(String indicator) throws Exception {
        String url = String.format(BASE, indicator);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET().build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new Exception("HTTP " + resp.statusCode() + " — " + indicator);
        }

        JsonNode root = mapper.readTree(resp.body());
        if (!root.isArray() || root.size() < 2) {
            throw new Exception("Structure JSON inattendue pour " + indicator);
        }

        JsonNode data = root.get(1);
        if (data == null || data.isNull() || data.size() == 0) {
            throw new Exception("Données manquantes (World Bank) : " + indicator);
        }
        return data;
    }
}

