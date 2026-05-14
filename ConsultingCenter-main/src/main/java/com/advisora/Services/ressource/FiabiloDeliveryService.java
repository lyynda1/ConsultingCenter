package com.advisora.Services.ressource;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FiabiloDeliveryService {
    private static final String API_URL = "https://www.fiabilo.tn/api/v1/post.php";
    private static final String DEFAULT_TOKEN = "api remplace pour sÃ©curitÃ©";
    private static final String DEFAULT_CP = "1000";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Map<String, String> DOTENV_CACHE = new ConcurrentHashMap<>();

    public DispatchResult createShipment(ShipmentRequest req) {
        validate(req);

        String token = resolveToken();
        if (isBlank(token)) {
            return DispatchResult.failed("Token FIABILO manquant.");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("prix", formatMoney(req.getTotalPrice()));
        form.put("nom", trim(req.getRecipientFullName()));
        form.put("gouvernerat", trim(req.getCity()));
        form.put("ville", trim(req.getCity()));
        form.put("adresse", trim(req.getAddressLine()));
        form.put("cp", defaultIfBlank(trim(req.getPostalCode()), DEFAULT_CP));
        form.put("tel", defaultIfBlank(trim(req.getPhone()), "00000000"));
        if (!isBlank(req.getPhone2())) {
            form.put("tel2", trim(req.getPhone2()));
        }
        form.put("designation", trim(req.getDesignation()));
        form.put("nb_article", String.valueOf(Math.max(1, req.getQuantity())));
        form.put("msg", defaultIfBlank(trim(req.getMessage()), "Commande marketplace"));
        form.put("echange", "0");
        form.put("article", "");
        form.put("nb_echange", "0");
        form.put("ouvrir", "0");
        form.put("token", token);

        try {
            String body = toFormEncoded(form);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return DispatchResult.failed("FIABILO indisponible (" + response.statusCode() + ").");
            }

            JsonNode root = safeJson(response.body());
            int status = root.path("status").asInt(0);
            String trackingCode = text(root, "status_message");
            String labelUrl = text(root, "lien");
            String etat = text(root, "etat");
            String motif = text(root, "motif");

            if (status == 1) {
                String message = isBlank(etat) ? "Expedition envoyee." : ("Expedition " + etat + ".");
                return DispatchResult.sent(message, trackingCode, labelUrl, etat, motif);
            }

            String fallbackMessage = !isBlank(motif) ? motif : defaultIfBlank(text(root, "status_message"), "Envoi livraison non confirme.");
            return DispatchResult.failed(fallbackMessage);
        } catch (Exception ex) {
            return DispatchResult.failed("Erreur envoi FIABILO: " + ex.getMessage());
        }
    }

    private void validate(ShipmentRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Livraison invalide.");
        }
        if (isBlank(req.getRecipientFullName())) {
            throw new IllegalArgumentException("Nom destinataire obligatoire.");
        }
        if (isBlank(req.getCity())) {
            throw new IllegalArgumentException("Ville obligatoire.");
        }
        if (isBlank(req.getAddressLine())) {
            throw new IllegalArgumentException("Adresse obligatoire.");
        }
        if (isBlank(req.getDesignation())) {
            throw new IllegalArgumentException("Designation ressource obligatoire.");
        }
        if (req.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantite livraison invalide.");
        }
    }

    private String resolveToken() {
        String env = System.getenv("FIABILO_API_TOKEN");
        if (!isBlank(env)) {
            return env.trim();
        }
        String fromDotEnv = readFromDotEnv("FIABILO_API_TOKEN");
        if (!isBlank(fromDotEnv)) {
            return fromDotEnv.trim();
        }
        return DEFAULT_TOKEN;
    }

    private String readFromDotEnv(String key) {
        ensureDotEnvLoaded();
        return DOTENV_CACHE.get(key);
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
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                DOTENV_CACHE.putIfAbsent(k, v);
            }
        } catch (IOException ignored) {
        }
    }

    private String toFormEncoded(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(defaultIfBlank(entry.getValue(), ""), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private JsonNode safeJson(String payload) {
        try {
            return JSON.readTree(payload);
        } catch (Exception ex) {
            return JSON.createObjectNode();
        }
    }

    private String text(JsonNode root, String key) {
        JsonNode node = root.path(key);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return isBlank(value) ? null : value.trim();
    }

    private String formatMoney(double amount) {
        return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0, amount));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class ShipmentRequest {
        private String recipientFullName;
        private String city;
        private String addressLine;
        private String phone;
        private String phone2;
        private String postalCode;
        private String designation;
        private int quantity;
        private double totalPrice;
        private String message;

        public String getRecipientFullName() {
            return recipientFullName;
        }

        public void setRecipientFullName(String recipientFullName) {
            this.recipientFullName = recipientFullName;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getAddressLine() {
            return addressLine;
        }

        public void setAddressLine(String addressLine) {
            this.addressLine = addressLine;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPhone2() {
            return phone2;
        }

        public void setPhone2(String phone2) {
            this.phone2 = phone2;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getDesignation() {
            return designation;
        }

        public void setDesignation(String designation) {
            this.designation = designation;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(double totalPrice) {
            this.totalPrice = totalPrice;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static final class DispatchResult {
        private final boolean sent;
        private final String message;
        private final String trackingCode;
        private final String labelUrl;
        private final String deliveryState;
        private final String reason;

        private DispatchResult(boolean sent, String message, String trackingCode, String labelUrl, String deliveryState, String reason) {
            this.sent = sent;
            this.message = message;
            this.trackingCode = trackingCode;
            this.labelUrl = labelUrl;
            this.deliveryState = deliveryState;
            this.reason = reason;
        }

        public static DispatchResult sent(String message, String trackingCode, String labelUrl, String deliveryState, String reason) {
            return new DispatchResult(true, message, trackingCode, labelUrl, deliveryState, reason);
        }

        public static DispatchResult failed(String message) {
            return new DispatchResult(false, message, null, null, null, null);
        }

        public boolean isSent() {
            return sent;
        }

        public String getMessage() {
            return message;
        }

        public String getTrackingCode() {
            return trackingCode;
        }

        public String getLabelUrl() {
            return labelUrl;
        }

        public String getDeliveryState() {
            return deliveryState;
        }

        public String getReason() {
            return reason;
        }
    }
}

