package com.advisora.utils;



import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class TranslateLight {

    private TranslateLight() {}

    // Small FR->EN keyword dictionary (extend anytime)
    private static final LinkedHashMap<String, String> FR2EN = new LinkedHashMap<>();
    static {
        // Tunisia / politics / institutions
        FR2EN.put("tunisie", "tunisia");
        FR2EN.put("tunis", "tunis");
        FR2EN.put("kaïs saïed", "kais saied");
        FR2EN.put("saied", "saied");
        FR2EN.put("gouvernement", "government");
        FR2EN.put("état", "state");

        // Economy / finance
        FR2EN.put("économie", "economy");
        FR2EN.put("crise", "crisis");
        FR2EN.put("inflation", "inflation");
        FR2EN.put("récession", "recession");
        FR2EN.put("dinar", "dinar");
        FR2EN.put("devise", "currency");
        FR2EN.put("taux de change", "exchange rate");
        FR2EN.put("budget", "budget");
        FR2EN.put("déficit", "deficit");
        FR2EN.put("dette", "debt");
        FR2EN.put("fmi", "imf");
        FR2EN.put("banque mondiale", "world bank");
        FR2EN.put("impôt", "tax");
        FR2EN.put("taxe", "tax");
        FR2EN.put("subvention", "subsidy");
        FR2EN.put("prix", "prices");
        FR2EN.put("augmentation", "increase");
        FR2EN.put("baisse", "decrease");
        FR2EN.put("marché", "market");
        FR2EN.put("investissement", "investment");
        FR2EN.put("export", "export");
        FR2EN.put("exportation", "export");
        FR2EN.put("import", "import");
        FR2EN.put("importation", "import");
        FR2EN.put("pénurie", "shortage");
        FR2EN.put("grève", "strike");
        FR2EN.put("rupture", "shortage");

        // Public sector / governance
        FR2EN.put("entreprises publiques", "state-owned enterprises");
        FR2EN.put("entreprise publique", "state-owned enterprise");
        FR2EN.put("privatisation", "privatization");
        FR2EN.put("corruption", "corruption");
        FR2EN.put("fraude", "fraud");
        FR2EN.put("abus", "abuse");

        // Health / pandemic
        FR2EN.put("santé", "health");
        FR2EN.put("pandémie", "pandemic");
        FR2EN.put("épidémie", "outbreak");
        FR2EN.put("virus", "virus");
        FR2EN.put("covid", "covid");
        FR2EN.put("vaccin", "vaccine");
        FR2EN.put("quarantaine", "quarantine");
    }

    // Small AR->EN keyword dictionary (optional but useful)
    private static final LinkedHashMap<String, String> AR2EN = new LinkedHashMap<>();
    static {
        AR2EN.put("تونس", "tunisia");
        AR2EN.put("تونسية", "tunisia");
        AR2EN.put("اقتصاد", "economy");
        AR2EN.put("الأزمة", "crisis");
        AR2EN.put("تضخم", "inflation");
        AR2EN.put("الدينار", "dinar");
        AR2EN.put("سعر الصرف", "exchange rate");
        AR2EN.put("الميزانية", "budget");
        AR2EN.put("ديون", "debt");
        AR2EN.put("ضرائب", "tax");
        AR2EN.put("إضراب", "strike");
        AR2EN.put("نقص", "shortage");
        AR2EN.put("فساد", "corruption");
        AR2EN.put("شركات عمومية", "state-owned enterprises");
        AR2EN.put("صحة", "health");
        AR2EN.put("وباء", "pandemic");
        AR2EN.put("تفشي", "outbreak");
    }

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    /**
     * Lightweight "translation": returns English keyword hints.
     * It does NOT aim to translate full sentences, only key concepts.
     */
    public static String translateLight(String input) {
        if (input == null) return "";
        String original = input.trim();
        if (original.isEmpty()) return "";

        // Normalize (remove accents) for FR matching
        String norm = normalize(original);

        // Collect hints without duplicates
        LinkedHashSet<String> hints = new LinkedHashSet<>();

        // Keep some original tokens too (helpful if already EN)
        for (String token : norm.split("\\s+")) {
            if (token.length() >= 4) hints.add(token);
        }

        // Phrase-level replacements first (order matters)
        for (Map.Entry<String, String> e : FR2EN.entrySet()) {
            String fr = normalize(e.getKey());
            if (norm.contains(fr)) hints.add(e.getValue());
        }

        // Arabic keywords (no accent removal needed)
        for (Map.Entry<String, String> e : AR2EN.entrySet()) {
            if (original.contains(e.getKey())) hints.add(e.getValue());
        }

        // Safety: cap output size
        String out = String.join(", ", hints);
        if (out.length() > 220) out = out.substring(0, 220).trim();

        return out;
    }

    private static String normalize(String s) {
        String t = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD);
        t = t.replaceAll("\\p{M}+", ""); // remove diacritics
        t = t.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        t = MULTI_SPACE.matcher(t).replaceAll(" ").trim();
        return t;
    }
}
