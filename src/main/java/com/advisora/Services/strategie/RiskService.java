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
    private final OllamaClient llm;
   // (you kept OllamaClient but it calls Gemini now)

    public RiskService(ExternalEventStore store, OllamaClient llm) {
        this.store = store;
        this.llm = llm;

    }

    public static class RiskResult {

        public Severity maxSeverity = Severity.INFO;
        public String message = "";
        public List<String> suggestions = new ArrayList<>();
    }

    // -----------------------------
    // 1) Macro check (keywords)
    // -----------------------------
    private static boolean hasAnyFuzzyWithSuggestions(String t, List<String> suggestionsOut, String... words) {
        if (t == null || t.isBlank()) return false;

        String[] tokens = t.split(" ");
        boolean matched = false;

        for (String w : words) {
            if (w == null || w.isBlank()) continue;
            String kw = norm(w);

            // exact contains
            if (t.contains(kw)) {
                matched = true;
                continue;
            }

            if (kw.length() < 5) continue;

            int maxDist = (kw.length() >= 10) ? 2 : 1;

            for (String tok : tokens) {
                if (tok.length() < 3) continue;
                if (Math.abs(tok.length() - kw.length()) > maxDist) continue;

                int d = levenshtein(tok, kw);
                if (d <= maxDist) {
                    matched = true;

                    // add suggestion only if it's really different
                    if (!tok.equals(kw)) {
                        String sug = tok + " → " + kw;
                        if (!suggestionsOut.contains(sug)) suggestionsOut.add(sug);
                    }
                    break;
                }
            }
        }
        return matched;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1];
        int[] cur  = new int[m + 1];

        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[m];
    }
    public static String applyCorrectionsToTitle(String originalTitle, List<String> suggestions) {
        if (originalTitle == null) return null;
        if (suggestions == null || suggestions.isEmpty()) return originalTitle;

        String corrected = originalTitle;

        for (String sug : suggestions) {
            if (sug == null) continue;
            String[] parts = sug.split("\\s*→\\s*");
            if (parts.length != 2) continue;

            String wrong = parts[0].trim();
            String right = parts[1].trim();
            if (wrong.isEmpty() || right.isEmpty()) continue;

            // Replace as a whole word (case-insensitive)
            corrected = corrected.replaceAll("(?i)\\b" + Pattern.quote(wrong) + "\\b", right);
        }
        return corrected;
    }
/**
 * This method checks a strategy title against risk-related keywords and active external events.
 * It determines if the title relates to economy or health topics and checks against active external events.
 *
 * @param strategyTitle The title of the strategy to be checked
 * @return RiskResult containing the severity level and relevant messages
 * @throws Exception If an error occurs during processing
 */
    public RiskResult checkTitle(String strategyTitle) throws Exception {
    // Normalize the input title for consistent comparison
        String t = norm(strategyTitle);

    // Initialize result object
        RiskResult r = new RiskResult();
    // Check for economy-related keywords in the normalized title
        boolean economy = hasAnyFuzzyWithSuggestions(t, r.suggestions,
                // =========================
                // EN — Economy / Finance
                // =========================
                "economy","economic","macroeconomy","macro","recession","slowdown","growth","gdp","output",
                "inflation","deflation","stagflation","cpi","ppi","price","prices","pricing","cost","costs",
                "budget","spending","expenditure","fiscal","austerity","stimulus","subsidy","subsidies",
                "tax","taxes","taxation","vat","tariff","tariffs","duty","customs",
                "trade","import","imports","export","exports","trade deal","trade war",
                "currency","fx","forex","exchange rate","devaluation","devalue","appreciation","peg",
                "dinar","dollar","euro","pound","yen",
                "interest","interest rate","rates","hike","cut","tightening","easing",
                "central bank","fed","ecb","imf","world bank","oecd",
                "bond","bonds","treasury","yield","yields","spread","default","credit rating",
                "bank","banking","bankruptcy","insolvency","bailout",
                "loan","loans","lending","borrow","borrowing","debt","public debt","sovereign debt",
                "credit","mortgage","refinance",
                "investment","investor","investors","fund","funds","asset","assets","portfolio",
                "market","markets","stock","stocks","equity","shares","index","indices",
                "dividend","earnings","revenue","profit","profits","loss","losses","margin","cashflow",
                "valuation","ipo","merger","acquisition","m&a","takeover",
                "startup","venture","vc","private equity",
                "salary","salaries","wage","wages","payroll","minimum wage",
                "unemployment","jobs","jobless","labor market","employment",
                "poverty","inequality","purchasing power","living cost","cost of living",
                "consumer","consumption","demand","supply","supply chain",
                "energy prices","oil","gas","brent","wti","commodity","commodities","gold","wheat",
                "tourism revenue","remittances","foreign reserves","reserves",
                "sanctions","embargo",
                "financial","finance","financing","funding","grant","grants","aid","donor",
                "public finance","state budget","budget deficit","deficit","surplus",
                "debt restructuring","rescheduling","write-off",

                // =========================
                // FR — Économie / Finance (sans accents)
                // =========================
                "economie","economique","macroeconomie","macro","recession","ralentissement","croissance",
                "pib","production",
                "inflation","deflation","stagflation","ipc","ipp",
                "prix","prix a la consommation","tarification","cout","couts","charges","achats",
                "budget","depense","depenses","depenses publiques","fiscal","austerite","relance","stimulus",
                "subvention","subventions","aide","aides",
                "taxe","taxes","taxation","tva","droit de douane","douane","tarif","tarifs",
                "commerce","echange","import","imports","importation","export","exports","exportation",
                "accord commercial","guerre commerciale",
                "devise","change","taux de change","devaluation","appreciation",
                "dinar","dollar","euro","livre","yen",
                "taux","taux directeur","taux d interet","hausse des taux","baisse des taux",
                "banque centrale","bct","fmi","banque mondiale","ocde",
                "obligation","obligations","bon du tresor","rendement","spread","defaut","notation",
                "banque","bancaire","faillite","insolvabilite","sauvetage",
                "pret","prets","credit","credits","endettement","dette","dette publique","dette souveraine",
                "hypotheque","refinancement",
                "investissement","investisseur","investisseurs","fonds","actif","actifs","portefeuille",
                "marche","marches","bourse","actions","indice",
                "dividende","resultats","chiffre d affaires","revenu","recette","recettes",
                "profit","profits","benefice","benefices","perte","pertes","marge","cash flow",
                "valorisation","introduction en bourse","ipo",
                "fusion","acquisition","rachat","m&a",
                "startup","capital risque","vc","private equity",
                "salaire","salaires","paie","smic","salaire minimum",
                "chomage","emploi","marché du travail","pouvoir d achat","cout de la vie",
                "consommateur","consommation","demande","offre","chaine d approvisionnement",
                "prix de l energie","petrole","gaz","matiere premiere","matieres premieres","or","ble",
                "tourisme","recettes touristiques","transferts","remises",
                "reserves","reserves de change",
                "sanctions","embargo",
                "financier","finance","financement","financer","subventionner","partenariat","collaboration",
                "deficit","deficit budgetaire","surplus",
                "restructuration de la dette","reechelonnement","annulation de dette",

                // =========================
                // AR — اقتصاد / مالية
                // =========================
                "اقتصاد","اقتصادي","الاقتصاد","نمو","تباطؤ","ركود","كساد","ناتج","الناتج","ناتج محلي","ناتج داخلي",
                "تضخم","انكماش","غلاء","ارتفاع الاسعار","اسعار","سعر","سعر الصرف","صرف",
                "ميزانية","موازنة","انفاق","نفقات","عجز","فائض","مالية عامة",
                "ضرائب","ضريبة","جباية","اداءات","tva","رسوم","جمارك","تعرفة","تعريفات",
                "تجارة","تبادل","استيراد","توريد","واردات","تصدير","صادرات","اتفاق تجاري",
                "عملة","عملات","دينار","دولار","يورو","جنيه","ين",
                "فائدة","فوائد","نسبة فائدة","معدلات الفائدة","رفع الفائدة","خفض الفائدة",
                "بنك","بنوك","قطاع بنكي","مصرف","مصارف","بنك مركزي","البنك المركزي",
                "قرض","قروض","اقتراض","تمويل","تمويلات","ممولة","ممول",
                "دين","ديون","مديونية","دَين عام","دين عمومي","دين سيادي",
                "سند","سندات","اذون خزينة","عائد","عوائد","تصنيف ائتماني","تعثر","افلاس",
                "استثمار","استثمارات","مستثمر","مستثمرون","صندوق","صناديق",
                "اسهم","سهم","بورصة","سوق","اسواق","مؤشر",
                "ارباح","ربح","خسائر","خسارة","ايرادات","ايراد","دخل","مداخيل","عائدات",
                "راتب","رواتب","اجور","اجر","حد ادنى للاجور","بطالة","عمل","وظائف",
                "قدرة شرائية","غلاء المعيشة","تكلفة المعيشة","تكلفة","تكاليف",
                "عرض","طلب","سلسلة التوريد",
                "نفط","غاز","طاقة","اسعار النفط","ذهب","قمح","سلع","مواد اولية",
                "سياحة","عائدات السياحة","تحويلات","احتياطي","احتياطات",
                "صندوق النقد","fmi","البنك الدولي",
                "دعم","منحة","منح","مساعدات","اعانة","شراكة","تعاون",
                "عقوبات","حظر","حصار"
        );

        boolean health = hasAnyFuzzyWithSuggestions(t,r.suggestions,
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