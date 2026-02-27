package com.advisora.Services.ressource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShopPaymentGatewayService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Map<String, String> DOTENV_CACHE = new ConcurrentHashMap<>();
    private static final String STRIPE_CHECKOUT_CREATE_URL = "https://api.stripe.com/v1/checkout/sessions";
    private static final String STRIPE_CHECKOUT_VERIFY_URL = "https://api.stripe.com/v1/checkout/sessions/";
    private static final String STRIPE_PAYMENT_INTENT_VERIFY_URL = "https://api.stripe.com/v1/payment_intents/";
    private static final String DEFAULT_STRIPE_PUBLISHABLE_KEY = "";
    private static final String DEFAULT_STRIPE_SECRET_KEY = "";

    public PaymentInitResult createPayment(String provider, double amountMoney, String clientRef) {
        String prefix = normalizeProviderPrefix(provider);
        if ("STRIPE".equals(prefix)) {
            return createStripeCheckoutSession(provider, amountMoney, clientRef);
        }

        String createUrl = config(prefix + "_CREATE_URL");
        String apiKey = config(prefix + "_API_KEY");
        String currency = configOrDefault("SHOP_CURRENCY", "TND");

        if (isBlank(createUrl) || isBlank(apiKey)) {
            return PaymentInitResult.localFallback(provider, "LOCAL-" + prefix + "-" + System.currentTimeMillis(), null,
                    "Mode simple (config API manquante).");
        }

        try {
            String payload = "{\"amount\":" + amountMoney
                    + ",\"currency\":\"" + escapeJson(currency) + "\""
                    + ",\"client_reference\":\"" + escapeJson(clientRef) + "\""
                    + ",\"description\":\"Wallet topup\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(createUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("apikey", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentInitResult.localFallback(provider, "LOCAL-" + prefix + "-" + System.currentTimeMillis(), null,
                        "API create non disponible (" + response.statusCode() + ").");
            }

            JsonNode root = safeJson(response.body());
            String externalRef = pickFirst(root, "payment_id", "id", "reference", "token", "tracking_id", "paymentId");
            if (isBlank(externalRef)) {
                externalRef = clientRef;
            }
            String paymentUrl = pickFirst(root, "payment_url", "url", "link", "checkout_url", "redirect_url", "payUrl");
            return PaymentInitResult.api(provider, externalRef, paymentUrl, "Session paiement creee.");
        } catch (Exception ex) {
            return PaymentInitResult.localFallback(provider, "LOCAL-" + prefix + "-" + System.currentTimeMillis(), null,
                    "Erreur API create, bascule mode simple.");
        }
    }

    public boolean verifyPayment(String provider, String externalRef, String paymentUrl) {
        String prefix = normalizeProviderPrefix(provider);
        if ("STRIPE".equals(prefix)) {
            return verifyStripeCheckoutSession(externalRef);
        }

        String verifyTemplate = config(prefix + "_VERIFY_URL");
        String apiKey = config(prefix + "_API_KEY");

        if (isBlank(verifyTemplate) || isBlank(apiKey) || isBlank(externalRef)) {
            return false;
        }

        String url = verifyTemplate.contains("{ref}")
                ? verifyTemplate.replace("{ref}", URLEncoder.encode(externalRef, StandardCharsets.UTF_8))
                : verifyTemplate + externalRef;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("apikey", apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            JsonNode root = safeJson(response.body());
            String status = pickFirst(root, "status", "payment_status", "state", "paymentState", "result");
            if (isBlank(status)) {
                return false;
            }
            String normalized = status.trim().toLowerCase();
            return normalized.contains("paid")
                    || normalized.contains("success")
                    || normalized.contains("completed")
                    || normalized.contains("accepted")
                    || normalized.contains("authorized");
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeProviderPrefix(String provider) {
        if (provider == null) {
            return "FLOUCI";
        }
        String normalized = provider.trim().toUpperCase();
        if (normalized.startsWith("STRIPE")) {
            return "STRIPE";
        }
        if (normalized.startsWith("D17")) {
            return "D17";
        }
        return "FLOUCI";
    }

    private PaymentInitResult createStripeCheckoutSession(String provider, double amountMoney, String clientRef) {
        String secretKey = resolveStripeSecretKey();
        String publishableKey = resolveStripePublishableKey();
        if (isBlank(secretKey) || isBlank(publishableKey)) {
            return PaymentInitResult.localFallback(provider, "LOCAL-STRIPE-" + System.currentTimeMillis(), null,
                    "Mode simple (Stripe keys manquantes).");
        }
        try {
            String currency = configOrDefault("STRIPE_CURRENCY", "usd").toLowerCase();
            long amountCents = Math.max(1L, Math.round(amountMoney * 100.0));
            String successUrl = configOrDefault("STRIPE_SUCCESS_URL", "https://example.com/success?session_id={CHECKOUT_SESSION_ID}");
            String cancelUrl = configOrDefault("STRIPE_CANCEL_URL", "https://example.com/cancel");

            String body = "mode=payment"
                    + "&success_url=" + urlEnc(successUrl)
                    + "&cancel_url=" + urlEnc(cancelUrl)
                    + "&metadata[client_reference]=" + urlEnc(clientRef)
                    + "&line_items[0][quantity]=1"
                    + "&line_items[0][price_data][currency]=" + urlEnc(currency)
                    + "&line_items[0][price_data][unit_amount]=" + amountCents
                    + "&line_items[0][price_data][product_data][name]=" + urlEnc("Wallet topup")
                    + "&line_items[0][price_data][product_data][description]=" + urlEnc("Mini Shop wallet recharge");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STRIPE_CHECKOUT_CREATE_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Bearer " + secretKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentInitResult.localFallback(provider, "LOCAL-STRIPE-" + System.currentTimeMillis(), null,
                        "Stripe create non disponible (" + response.statusCode() + ").");
            }
            JsonNode root = safeJson(response.body());
            String sessionId = pickFirst(root, "id");
            String paymentUrl = pickFirst(root, "url");
            if (isBlank(sessionId)) {
                sessionId = "LOCAL-STRIPE-" + System.currentTimeMillis();
            }
            String note = "Stripe checkout cree";
            if (isBlank(paymentUrl)) {
                note = "Stripe session creee sans URL de redirection.";
            }
            return PaymentInitResult.api(provider, sessionId, paymentUrl, note);
        } catch (Exception ex) {
            return PaymentInitResult.localFallback(provider, "LOCAL-STRIPE-" + System.currentTimeMillis(), null,
                    "Erreur Stripe create, bascule mode simple.");
        }
    }

    private boolean verifyStripeCheckoutSession(String externalRef) {
        String secretKey = resolveStripeSecretKey();
        if (isBlank(secretKey) || isBlank(externalRef)) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STRIPE_CHECKOUT_VERIFY_URL + urlEnc(externalRef)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secretKey)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            JsonNode root = safeJson(response.body());
            String paymentStatus = pickFirst(root, "payment_status");
            if ("paid".equalsIgnoreCase(paymentStatus)) {
                return true;
            }
            String paymentIntentId = pickFirst(root, "payment_intent");
            if (!isBlank(paymentIntentId)) {
                return verifyStripePaymentIntent(secretKey, paymentIntentId);
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean verifyStripePaymentIntent(String secretKey, String paymentIntentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STRIPE_PAYMENT_INTENT_VERIFY_URL + urlEnc(paymentIntentId)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secretKey)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            JsonNode root = safeJson(response.body());
            String status = pickFirst(root, "status");
            return "succeeded".equalsIgnoreCase(status);
        } catch (Exception ex) {
            return false;
        }
    }

    private String resolveStripeSecretKey() {
        String key = config("STRIPE_SECRET_KEY");
        if (isBlank(key)) {
            key = config("STRIPE_API_KEY");
        }
        if (isBlank(key)) {
            key = DEFAULT_STRIPE_SECRET_KEY;
        }
        return key;
    }

    private String resolveStripePublishableKey() {
        String key = config("STRIPE_PUBLISHABLE_KEY");
        if (isBlank(key)) {
            key = DEFAULT_STRIPE_PUBLISHABLE_KEY;
        }
        return key;
    }

    private String urlEnc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String configOrDefault(String key, String fallback) {
        String value = config(key);
        return isBlank(value) ? fallback : value;
    }

    private String config(String key) {
        String env = System.getenv(key);
        if (!isBlank(env)) {
            return env.trim();
        }
        ensureDotEnvLoaded();
        String fromFile = DOTENV_CACHE.get(key);
        return fromFile == null ? null : fromFile.trim();
    }

    private void ensureDotEnvLoaded() {
        if (!DOTENV_CACHE.isEmpty()) {
            return;
        }
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            return;
        }
        try {
            for (String raw : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                DOTENV_CACHE.putIfAbsent(key, value);
            }
        } catch (IOException ignored) {
        }
    }

    private JsonNode safeJson(String payload) {
        if (isBlank(payload)) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(payload);
        } catch (Exception ex) {
            return MAPPER.createObjectNode();
        }
    }

    private String pickFirst(JsonNode root, String... names) {
        if (root == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            JsonNode node = root.get(name);
            if (node != null && !node.isNull()) {
                String v = node.asText();
                if (!isBlank(v)) {
                    return v.trim();
                }
            }
        }
        return null;
    }

    private String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class PaymentInitResult {
        private final String provider;
        private final String externalRef;
        private final String paymentUrl;
        private final String note;
        private final boolean apiMode;

        private PaymentInitResult(String provider, String externalRef, String paymentUrl, String note, boolean apiMode) {
            this.provider = provider;
            this.externalRef = externalRef;
            this.paymentUrl = paymentUrl;
            this.note = note;
            this.apiMode = apiMode;
        }

        public static PaymentInitResult api(String provider, String externalRef, String paymentUrl, String note) {
            return new PaymentInitResult(provider, externalRef, paymentUrl, note, true);
        }

        public static PaymentInitResult localFallback(String provider, String externalRef, String paymentUrl, String note) {
            return new PaymentInitResult(provider, externalRef, paymentUrl, note, false);
        }

        public String getProvider() {
            return provider;
        }

        public String getExternalRef() {
            return externalRef;
        }

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public String getNote() {
            return note;
        }

        public boolean isApiMode() {
            return apiMode;
        }
    }
}
