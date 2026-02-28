package com.advisora.Services.strategie;

import com.advisora.Model.strategie.ExternalEvent;
import com.advisora.Model.strategie.LlmDecision;
import com.advisora.Model.strategie.NewsItem;
import com.advisora.enums.Severity;
import com.advisora.utils.news.OllamaClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class RiskService {

    private static final ObjectMapper OM = new ObjectMapper();

    private final ExternalEventStore store;
    private final OllamaClient llm; // (you kept OllamaClient but it calls Gemini now)

    public RiskService(ExternalEventStore store, OllamaClient llm) {
        this.store = store;
        this.llm = llm;
    }

    public static class RiskResult {
        public Severity maxSeverity = Severity.INFO;
        public String message = "";
    }

    // -----------------------------
    // 1) Macro check (keywords)
    // -----------------------------
    public RiskResult checkTitle(String strategyTitle) throws Exception {
        String t = norm(strategyTitle);


        boolean economy = hasAny(t,
                // EN
                "price","pricing","budget","investment","loan","cost","profit","revenue","salary",
                "inflation","import","export","currency","dinar","interest","tax","subsidy",
                // FR (normalized without accents)
                "prix","tarification","budget","investissement","pret","credit","cout","profit",
                "benefice","revenu","salaire","inflation","importation","exportation","devise",
                "taux de change","dinar","interet","taxe","impot","subvention",
                // AR
                "اقتصاد","سعر","اسعار","ميزانية","استثمار","قرض","تكلفة","ارباح","إيرادات","رواتب",
                "تضخم","توريد","استيراد","تصدير","عملة","دينار","فائدة","ضرائب","دعم"
        );

        boolean health = hasAny(t,
                // EN
                "health","pandemic","outbreak","covid","travel","tourism","event","disease","virus",
                // FR
                "sante","pandemie","epidemie","alerte sanitaire","voyage","tourisme","evenement","maladie","virus",
                // AR
                "صحة","وباء","جائحة","تفشي","كوفيد","سفر","سياحة","حدث","مرض","فيروس"
        );


        List<ExternalEvent> active = store.getActiveEvents();
        System.out.println("title norm = " + t);
        System.out.println("economy=" + economy + " health=" + health);

        for (ExternalEvent e : active) {
            System.out.println("eventType=" + e.getEventType() + " severity=" + e.getSeverity() + " name=" + e.getEventName());
        }

        RiskResult r = new RiskResult();
        StringBuilder sb = new StringBuilder();

        for (ExternalEvent e : active) {
            String et = norm(e.getEventType()); // reuse your norm()
            boolean isEconomyType = et.contains("economy") || et.contains("economic") || et.contains("economie");
            boolean isHealthType  = et.contains("health")  || et.contains("sanitaire") || et.contains("sante");

            boolean relevant =
                    (economy && isEconomyType) ||
                            (health && isHealthType);

            if (!relevant) continue;

            r.maxSeverity = Severity.max(r.maxSeverity, e.getSeverity());

            sb.append("[").append(e.getEventType()).append("][").append(e.getSeverity()).append("] ")
                    .append(e.getEventName()).append("\n")
                    .append(e.getDescription() == null ? "" : e.getDescription())
                    .append("\n----------------------\n");
        }

        r.message = sb.length() == 0
                ? "Aucun événement externe actif ne correspond à ce titre."
                : sb.toString();

// if keywords indicate risk topic, raise at least WARNING
        if (economy || health) {
            r.maxSeverity = Severity.WARNING;
        }

        return r;
    }
    public List<ExternalEvent> getActiveEvents() throws Exception {
        return store.getActiveEvents();
    }
    // -----------------------------
    // 2) LLM decision (no matches/scoring)
    // -----------------------------
    public LlmDecision decideWithLLM(String title,
                                     List<ExternalEvent> activeEvents,
                                     List<NewsItem> recentNews) throws Exception {

        StringBuilder eventsTxt = new StringBuilder();
        for (ExternalEvent e : activeEvents) {
            eventsTxt.append("- ").append(e.getEventType()).append(" / ").append(e.getSeverity())
                    .append(" : ").append(e.getEventName())
                    .append(" | ").append(safe(e.getDescription()))
                    .append("\n");
        }

        StringBuilder newsTxt = new StringBuilder();
        int maxNews = Math.min(recentNews.size(), 8);
        for (int i = 0; i < maxNews; i++) {
            NewsItem n = recentNews.get(i);
            newsTxt.append("- ").append(safe(n.title)).append(" | ").append(safe(n.url)).append("\n");
        }

        String prompt = """
Tu es un expert en gestion des risques en Tunisie.

Tâche: décider si la stratégie doit être autorisée ou non selon les événements externes.
Langues: le titre et les news peuvent être en n'importe quelle langue.

Règles strictes:
- Si aucun lien clair -> INFO
- Si lien faible mais plausible -> WARNING
- Si lien fort et risque élevé -> DANGER
- Si lien très fort + risque critique -> CRITICAL

Répond STRICTEMENT en JSON (aucun texte hors JSON) avec EXACTEMENT ces clés :
{
  "finalSeverity": "INFO|WARNING|DANGER|CRITICAL",
  "summary_fr": "1 phrase",
  "why_fr": "1-2 phrases",
  "matchedNewsLinks": ["url1", "url2", "..."]
}

Titre stratégie:
"%s"

Événements actifs:
%s

News récentes:
%s
""".formatted(title, eventsTxt, newsTxt);

        String out = llm.generate(prompt);
        System.out.println("[LLM RAW]\n" + out);

        String jsonOnly = extractJsonObject(out);
        System.out.println("[LLM JSON ONLY]\n" + jsonOnly);
        JsonNode root = OM.readTree(jsonOnly);

        LlmDecision d = new LlmDecision();
        d.finalSeverity = Optional.ofNullable(firstText(root,
                "finalSeverity",
                "Decision finale sur la sevérité",
                "Decision finale sur la severite",
                "severity"
        )).orElse("INFO");

        d.summary_fr = Optional.ofNullable(firstText(root,
                "summary_fr",
                "Résumé",
                "Resume",
                "summary"
        )).orElse("");

        d.why_fr = Optional.ofNullable(firstText(root,
                "why_fr",
                "raison",
                "reason"
        )).orElse("");
// Extract matched news links
        JsonNode linksNode = root.path("matchedNewsLinks");
        if (linksNode.isArray()) {
            d.matchedNewsLinks = new ArrayList<>();
            for (JsonNode link : linksNode) {
                d.matchedNewsLinks.add(link.asText());
            }
        }
        return d;


    }


    private static String firstText(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && !n.isNull()) {
                String v = n.asText();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    // -----------------------------
    // Helpers
    // -----------------------------
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private static String norm(String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase(Locale.ROOT);

        // remove accents (FR)
        x = Normalizer.normalize(x, Normalizer.Form.NFD);
        x = x.replaceAll("\\p{M}+", "");

        // keep letters/numbers/spaces including Arabic
        x = x.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        x = MULTI_SPACE.matcher(x).replaceAll(" ").trim();
        return x;
    }

    private static boolean hasAny(String t, String... words) {
        if (t == null || t.isBlank()) return false;
        for (String w : words) {
            if (w == null || w.isBlank()) continue;
            if (t.contains(w)) return true;
        }
        return false;
    }

    // Extract first {...} block (Gemini/Qwen may add text)
    public static String extractJsonObject(String s) {
        if (s == null) throw new RuntimeException("LLM returned null");
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b < 0 || b <= a) {
            String snippet = s.length() > 300 ? s.substring(0, 300) : s;
            throw new RuntimeException("LLM did not return JSON. Raw: " + snippet);
        }
        return s.substring(a, b + 1).trim();
    }
}