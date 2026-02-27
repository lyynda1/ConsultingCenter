package com.advisora.Services.ressource;

import com.advisora.Model.ressource.Ressource;
import com.advisora.enums.RessourceStatut;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceAdminAiCopilotService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_PROVIDER = "openai";
    private static final String DEFAULT_MODEL = "gpt-5";
    private static final String ANALYSIS_VERSION = "COPILOT_R1";

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int OVERSTOCK_THRESHOLD = 250;

    public AdminAnalysisResult analyzeForAdmin(List<Ressource> resources,
                                               Map<Integer, String> supplierNames,
                                               Map<Integer, Integer> reservedByResourceId) {
        List<Ressource> safeResources = resources == null ? List.of() : resources;
        Map<Integer, String> safeSuppliers = supplierNames == null ? Map.of() : supplierNames;
        Map<Integer, Integer> safeReserved = reservedByResourceId == null ? Map.of() : reservedByResourceId;

        AdminSignalReport report = buildSignalReport(safeResources, safeSuppliers, safeReserved);
        String localSummary = formatLocalSummary(report);
        String aiSummary = requestOpenAiSummary(localSummary);

        List<AdminActionItem> rows = flattenAndRankSignals(report);
        String source = isBlank(aiSummary)
                ? "LOCAL_RULES"
                : ("AI_" + resolveProvider().toUpperCase(Locale.ROOT) + "_" + resolveModel().toUpperCase(Locale.ROOT));
        if (isBlank(aiSummary)) {
            aiSummary = "Analyse IA indisponible. Rapport local calcule avec les regles metier.";
        }

        return new AdminAnalysisResult(
                ANALYSIS_VERSION,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                report.totalResources,
                report.lowStockCount,
                report.overstockCount,
                report.priceAnomalyCount,
                report.supplierRiskCount,
                rows,
                aiSummary.trim(),
                source
        );
    }

    public String generateAdminAnalysis(List<Ressource> resources,
                                        Map<Integer, String> supplierNames,
                                        Map<Integer, Integer> reservedByResourceId) {
        AdminAnalysisResult result = analyzeForAdmin(resources, supplierNames, reservedByResourceId);
        StringBuilder sb = new StringBuilder();
        sb.append("Analyse: ").append(result.getGeneratedAt()).append(" | Source: ").append(result.getSource()).append('\n');
        sb.append("Perimetre: ").append(result.getTotalResources()).append(" ressources").append('\n');
        sb.append("Stock critique=").append(result.getLowStockCount())
                .append(", Surstock=").append(result.getOverstockCount())
                .append(", Prix anormal=").append(result.getPriceAnomalyCount())
                .append(", Fournisseur risque=").append(result.getSupplierRiskCount()).append('\n');
        sb.append('\n').append(result.getAiSummary()).append('\n').append('\n');
        for (AdminActionItem item : result.getActions()) {
            sb.append("- [").append(item.getPriority()).append("] ")
                    .append(item.getActionCode()).append(" | ")
                    .append(item.getActionLabel())
                    .append(" (Confiance ").append(item.getConfidencePct()).append("%, delai ").append(item.getDeadline()).append(")")
                    .append('\n');
            sb.append("  Justification: ").append(item.getJustification()).append('\n');
        }
        return sb.toString().trim();
    }

    private AdminSignalReport buildSignalReport(List<Ressource> resources,
                                                Map<Integer, String> supplierNames,
                                                Map<Integer, Integer> reservedByResourceId) {
        AdminSignalReport report = new AdminSignalReport();
        report.totalResources = resources.size();

        double avgPrice = resources.stream().mapToDouble(Ressource::getPrixRs).average().orElse(0.0);
        double variance = resources.stream()
                .mapToDouble(r -> {
                    double d = r.getPrixRs() - avgPrice;
                    return d * d;
                }).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        Map<Integer, SupplierAgg> supplierAgg = new HashMap<>();

        for (Ressource r : resources) {
            if (r == null) {
                continue;
            }
            int reserved = Math.max(0, reservedByResourceId.getOrDefault(r.getIdRs(), 0));
            int available = Math.max(0, r.getQuantiteRs() - reserved);
            String supplier = supplierNames.getOrDefault(r.getIdFr(), "#" + r.getIdFr());

            SupplierAgg agg = supplierAgg.computeIfAbsent(r.getIdFr(), id -> new SupplierAgg(supplier));
            agg.total++;

            if (available <= LOW_STOCK_THRESHOLD) {
                agg.lowStock++;
                report.lowStockCount++;
                report.highPrioritySignals.add(new Signal(
                        "HAUTE",
                        "STOCK",
                        "Stock critique",
                        "Reapprovisionner " + safe(r.getNomRs()) + " (fournisseur " + supplier + ")",
                        "Stock dispo=" + available + " / reserve=" + reserved
                ));
            }

            if (r.getQuantiteRs() >= OVERSTOCK_THRESHOLD && reserved == 0) {
                report.overstockCount++;
                report.mediumPrioritySignals.add(new Signal(
                        "MOYENNE",
                        "STOCK",
                        "Surstock",
                        "Lancer promotion/location sur " + safe(r.getNomRs()),
                        "Quantite totale=" + r.getQuantiteRs() + ", aucune reservation active."
                ));
            }

            if (r.getAvailabilityStatusRs() == RessourceStatut.UNAVAILABLE) {
                agg.unavailable++;
            }

            if (stdDev > 0.0) {
                double z = Math.abs((r.getPrixRs() - avgPrice) / stdDev);
                if (z >= 1.8) {
                    report.priceAnomalyCount++;
                    report.mediumPrioritySignals.add(new Signal(
                            "MOYENNE",
                            "PRIX",
                            "Prix atypique",
                            "Verifier le prix de " + safe(r.getNomRs()),
                            "Prix=" + formatMoney(r.getPrixRs()) + " vs moyenne=" + formatMoney(avgPrice)
                    ));
                }
            }
        }

        for (SupplierAgg agg : supplierAgg.values()) {
            if (agg.total <= 0) {
                continue;
            }
            double unavailableRatio = ((double) agg.unavailable / agg.total);
            double lowStockRatio = ((double) agg.lowStock / agg.total);
            double riskScore = (unavailableRatio * 0.6 + lowStockRatio * 0.4) * 100.0;
            if (agg.total >= 2 && riskScore >= 45.0) {
                report.supplierRiskCount++;
                String priority = riskScore >= 60.0 ? "HAUTE" : "MOYENNE";
                Signal s = new Signal(
                        priority,
                        "FOURNISSEUR",
                        "Risque fournisseur",
                        "Lancer audit fournisseur " + agg.name,
                        "Risque=" + String.format(Locale.US, "%.1f", riskScore)
                                + "% (indispo=" + agg.unavailable + "/" + agg.total
                                + ", stock critique=" + agg.lowStock + "/" + agg.total + ")"
                );
                if ("HAUTE".equals(priority)) {
                    report.highPrioritySignals.add(s);
                } else {
                    report.mediumPrioritySignals.add(s);
                }
            }
        }

        if (report.highPrioritySignals.isEmpty()
                && report.mediumPrioritySignals.isEmpty()
                && report.lowPrioritySignals.isEmpty()) {
            report.lowPrioritySignals.add(new Signal(
                    "BASSE",
                    "PILOTAGE",
                    "Situation stable",
                    "Maintenir la surveillance reguliere",
                    "Aucune anomalie critique detectee."
            ));
        } else {
            report.lowPrioritySignals.add(new Signal(
                    "BASSE",
                    "PILOTAGE",
                    "Amelioration continue",
                    "Planifier un refresh hebdomadaire des seuils",
                    "Ajuster seuils stock/prix selon les reservations recentes."
            ));
        }
        return report;
    }

    private String requestOpenAiSummary(String localSummary) {
        String provider = resolveProvider();
        if (!"openai".equalsIgnoreCase(provider)) {
            return null;
        }
        String apiKey = resolveApiKey();
        if (isBlank(apiKey)) {
            return null;
        }
        String model = resolveModel();
        String systemPrompt = """
                Tu es un copilote admin pour le module Gestion Ressource.
                Tu dois repondre uniquement sur ce module: stock, prix, fournisseurs, reservations.
                Si la demande est hors module, reponds exactement: HORS_SCOPE_RESSOURCE.
                Reponse en francais, concise, orientee action.
                """;
        String userPrompt = """
                A partir du rapport ci-dessous, genere un plan decisionnel professionnel:
                1) Priorite haute (max 3 actions)
                2) Priorite moyenne (max 3 actions)
                3) Priorite basse (max 2 actions)
                Chaque action doit contenir: action + justification courte.
                Ne pas inventer de donnees hors rapport.

                RAPPORT:
                """ + localSummary;

        String text = callResponsesApi(apiKey, model, systemPrompt, userPrompt);
        if (!isBlank(text)) {
            return text.trim();
        }
        text = callChatCompletions(apiKey, model, systemPrompt, userPrompt);
        return isBlank(text) ? null : text.trim();
    }

    private String callResponsesApi(String apiKey, String model, String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("max_output_tokens", 650);
            ObjectNode reasoning = body.putObject("reasoning");
            reasoning.put("effort", "minimal");

            ArrayNode input = body.putArray("input");
            ObjectNode sys = input.addObject();
            sys.put("role", "system");
            ArrayNode sysContent = sys.putArray("content");
            sysContent.addObject().put("type", "input_text").put("text", systemPrompt);

            ObjectNode usr = input.addObject();
            usr.put("role", "user");
            ArrayNode usrContent = usr.putArray("content");
            usrContent.addObject().put("type", "input_text").put("text", userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_RESPONSES_URL))
                    .timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = safeJson(response.body());
            String text = root.path("output_text").asText("");
            if (!isBlank(text)) {
                return text;
            }
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    JsonNode content = item.path("content");
                    if (!content.isArray()) {
                        continue;
                    }
                    for (JsonNode c : content) {
                        String t = c.path("text").asText("");
                        if (!isBlank(t)) {
                            return t;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String callChatCompletions(String apiKey, String model, String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.2);

            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_CHAT_URL))
                    .timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode root = safeJson(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            return null;
        }
    }

    private List<AdminActionItem> flattenAndRankSignals(AdminSignalReport report) {
        List<Signal> all = new ArrayList<>();
        all.addAll(report.highPrioritySignals);
        all.addAll(report.mediumPrioritySignals);
        all.addAll(report.lowPrioritySignals);

        Set<String> seen = new LinkedHashSet<>();
        List<AdminActionItem> rows = all.stream()
                .filter(s -> seen.add((s.priority + "|" + s.category + "|" + s.action).toLowerCase(Locale.ROOT)))
                .sorted((a, b) -> {
                    int pa = priorityWeight(a.priority);
                    int pb = priorityWeight(b.priority);
                    if (pa != pb) {
                        return Integer.compare(pa, pb);
                    }
                    return a.action.compareToIgnoreCase(b.action);
                })
                .limit(18)
                .map(this::toActionItem)
                .collect(Collectors.toList());

        if (rows.isEmpty()) {
            rows = List.of(new AdminActionItem(
                    "BASSE",
                    "PILOTAGE",
                    "Stable",
                    "MONITOR",
                    "Maintenir la surveillance",
                    "Aucune anomalie detectee.",
                    62,
                    "7J",
                    "Stabilite operationnelle"
            ));
        }
        return rows;
    }

    private AdminActionItem toActionItem(Signal s) {
        String code = resolveActionCode(s);
        int confidence = resolveConfidence(s);
        String deadline = resolveDeadline(s.priority);
        String businessImpact = resolveBusinessImpact(s);
        return new AdminActionItem(
                s.priority,
                s.category,
                s.indicator,
                code,
                s.action,
                s.justification,
                confidence,
                deadline,
                businessImpact
        );
    }

    private String resolveActionCode(Signal s) {
        String category = safe(s.category).toUpperCase(Locale.ROOT);
        String indicator = safe(s.indicator).toUpperCase(Locale.ROOT);
        if ("STOCK".equals(category) && indicator.contains("CRITIQUE")) {
            return "REORDER";
        }
        if ("STOCK".equals(category) && indicator.contains("SURSTOCK")) {
            return "REDUCE_STOCK";
        }
        if ("PRIX".equals(category)) {
            return "REVIEW_PRICE";
        }
        if ("FOURNISSEUR".equals(category) && safe(s.justification).toUpperCase(Locale.ROOT).contains("RISQUE")) {
            return "ALERT_SUPPLIER";
        }
        if ("FOURNISSEUR".equals(category)) {
            return "SWITCH_SUPPLIER";
        }
        return "MONITOR";
    }

    private int resolveConfidence(Signal s) {
        int base;
        if ("HAUTE".equalsIgnoreCase(s.priority)) {
            base = 84;
        } else if ("MOYENNE".equalsIgnoreCase(s.priority)) {
            base = 74;
        } else {
            base = 62;
        }
        String category = safe(s.category).toUpperCase(Locale.ROOT);
        if ("STOCK".equals(category) || "PRIX".equals(category)) {
            base += 7;
        } else if ("FOURNISSEUR".equals(category)) {
            base += 4;
        }
        return Math.min(base, 95);
    }

    private String resolveDeadline(String priority) {
        if ("HAUTE".equalsIgnoreCase(priority)) {
            return "24H";
        }
        if ("MOYENNE".equalsIgnoreCase(priority)) {
            return "72H";
        }
        return "7J";
    }

    private String resolveBusinessImpact(Signal s) {
        String category = safe(s.category).toUpperCase(Locale.ROOT);
        if ("STOCK".equals(category)) {
            return "Disponibilite et taux de service";
        }
        if ("PRIX".equals(category)) {
            return "Marge et competitivite";
        }
        if ("FOURNISSEUR".equals(category)) {
            return "Continuite de supply chain";
        }
        return "Pilotage operationnel";
    }

    private int priorityWeight(String priority) {
        if ("HAUTE".equalsIgnoreCase(priority)) {
            return 1;
        }
        if ("MOYENNE".equalsIgnoreCase(priority)) {
            return 2;
        }
        return 3;
    }

    private String formatLocalSummary(AdminSignalReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Copilote Admin IA - Rapport Ressources\n");
        sb.append("Perimetre analyse: ").append(report.totalResources).append(" ressource(s)\n");
        sb.append("Stock critique: ").append(report.lowStockCount).append(" | ");
        sb.append("Surstock: ").append(report.overstockCount).append(" | ");
        sb.append("Anomalies prix: ").append(report.priceAnomalyCount).append(" | ");
        sb.append("Fournisseurs a risque: ").append(report.supplierRiskCount).append("\n\n");
        appendPrioritySection(sb, "PRIORITE HAUTE", report.highPrioritySignals);
        appendPrioritySection(sb, "PRIORITE MOYENNE", report.mediumPrioritySignals);
        appendPrioritySection(sb, "PRIORITE BASSE", report.lowPrioritySignals);
        return sb.toString().trim();
    }

    private void appendPrioritySection(StringBuilder sb, String title, List<Signal> signals) {
        sb.append(title).append("\n");
        if (signals == null || signals.isEmpty()) {
            sb.append("- Aucune action critique.\n\n");
            return;
        }
        List<Signal> top = signals.stream().limit(4).toList();
        for (Signal s : top) {
            sb.append("- [").append(s.category).append("] ").append(s.action).append("\n");
            sb.append("  Justification: ").append(s.justification).append("\n");
        }
        sb.append("\n");
    }

    private String resolveApiKey() {
        String key = config("OPENAI_API_KEY");
        if (isBlank(key)) {
            key = config("OPENAI_KEY");
        }
        return key;
    }

    private String resolveModel() {
        String model = config("AI_MODEL");
        if (isBlank(model)) {
            model = config("OPENAI_MODEL");
        }
        if (isBlank(model)) {
            model = config("OPENAI_ADMIN_MODEL");
        }
        if (isBlank(model)) {
            model = DEFAULT_MODEL;
        }
        return model;
    }

    private String resolveProvider() {
        String provider = config("AI_PROVIDER");
        if (isBlank(provider)) {
            provider = DEFAULT_PROVIDER;
        }
        return provider;
    }

    private String config(String key) {
        String env = System.getenv(key);
        if (!isBlank(env)) {
            return env.trim();
        }
        String prop = System.getProperty(key);
        return isBlank(prop) ? null : prop.trim();
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

    private String formatMoney(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "-" : value.trim();
    }

    public static final class AdminActionItem {
        private final String priority;
        private final String category;
        private final String indicator;
        private final String actionCode;
        private final String actionLabel;
        private final String justification;
        private final int confidencePct;
        private final String deadline;
        private final String businessImpact;

        public AdminActionItem(String priority, String category, String indicator, String action, String justification) {
            this(priority, category, indicator, "MONITOR", action, justification, 60, "7J", "Pilotage operationnel");
        }

        public AdminActionItem(String priority,
                               String category,
                               String indicator,
                               String actionCode,
                               String actionLabel,
                               String justification,
                               int confidencePct,
                               String deadline,
                               String businessImpact) {
            this.priority = priority;
            this.category = category;
            this.indicator = indicator;
            this.actionCode = actionCode;
            this.actionLabel = actionLabel;
            this.justification = justification;
            this.confidencePct = confidencePct;
            this.deadline = deadline;
            this.businessImpact = businessImpact;
        }

        public String getPriority() {
            return priority;
        }

        public String getCategory() {
            return category;
        }

        public String getIndicator() {
            return indicator;
        }

        public String getActionCode() {
            return actionCode;
        }

        public String getActionLabel() {
            return actionLabel;
        }

        public String getJustification() {
            return justification;
        }

        public int getConfidencePct() {
            return confidencePct;
        }

        public String getDeadline() {
            return deadline;
        }

        public String getBusinessImpact() {
            return businessImpact;
        }
    }

    public static final class AdminAnalysisResult {
        private final String analysisVersion;
        private final String generatedAt;
        private final int totalResources;
        private final int lowStockCount;
        private final int overstockCount;
        private final int priceAnomalyCount;
        private final int supplierRiskCount;
        private final List<AdminActionItem> actions;
        private final String aiSummary;
        private final String source;

        public AdminAnalysisResult(String analysisVersion,
                                   String generatedAt,
                                   int totalResources,
                                   int lowStockCount,
                                   int overstockCount,
                                   int priceAnomalyCount,
                                   int supplierRiskCount,
                                   List<AdminActionItem> actions,
                                   String aiSummary,
                                   String source) {
            this.analysisVersion = analysisVersion;
            this.generatedAt = generatedAt;
            this.totalResources = totalResources;
            this.lowStockCount = lowStockCount;
            this.overstockCount = overstockCount;
            this.priceAnomalyCount = priceAnomalyCount;
            this.supplierRiskCount = supplierRiskCount;
            this.actions = actions == null ? List.of() : List.copyOf(actions);
            this.aiSummary = aiSummary;
            this.source = source;
        }

        public String getAnalysisVersion() {
            return analysisVersion;
        }

        public String getGeneratedAt() {
            return generatedAt;
        }

        public int getTotalResources() {
            return totalResources;
        }

        public int getLowStockCount() {
            return lowStockCount;
        }

        public int getOverstockCount() {
            return overstockCount;
        }

        public int getPriceAnomalyCount() {
            return priceAnomalyCount;
        }

        public int getSupplierRiskCount() {
            return supplierRiskCount;
        }

        public List<AdminActionItem> getActions() {
            return actions;
        }

        public String getAiSummary() {
            return aiSummary;
        }

        public String getSource() {
            return source;
        }
    }

    private static class SupplierAgg {
        final String name;
        int total;
        int unavailable;
        int lowStock;

        SupplierAgg(String name) {
            this.name = name;
        }
    }

    private static class Signal {
        final String priority;
        final String category;
        final String indicator;
        final String action;
        final String justification;

        Signal(String priority, String category, String indicator, String action, String justification) {
            this.priority = priority;
            this.category = category;
            this.indicator = indicator;
            this.action = action;
            this.justification = justification;
        }
    }

    private static class AdminSignalReport {
        int totalResources;
        int lowStockCount;
        int overstockCount;
        int priceAnomalyCount;
        int supplierRiskCount;
        final List<Signal> highPrioritySignals = new ArrayList<>();
        final List<Signal> mediumPrioritySignals = new ArrayList<>();
        final List<Signal> lowPrioritySignals = new ArrayList<>();
    }
}
