package com.advisora.Services.strategie;

import com.advisora.Model.strategie.ExternalEvent;
import com.advisora.enums.Severity;
import com.advisora.utils.news.GdeltClient;
import com.advisora.utils.news.WhoClient;

public class EventAggregator {

    public ExternalEvent buildEconomyEventFromGdelt(GdeltClient.GdeltResult r) {
        ExternalEvent e = new ExternalEvent();
        e.setSource("GDELT");
        e.setEventType("ECONOMY");
        e.setEventName("Tunisia economy risk (last 48h)");
        e.setCurrentValue((double) r.count);
        e.setUnit("articles/48h");
        e.setSeverity(severityFromCount(r.count));

        e.setDescription("Economy news volume in last 48h = " + r.count
                + "\nThis is a macro risk signal. Detailed news is fetched based on the strategy title.");

        // âœ… only active if enough signals
        e.setActive(r.count >= 5);
        return e;
    }

    public ExternalEvent buildHealthEventFromWho(WhoClient.WhoResult r) {
        ExternalEvent e = new ExternalEvent();
        e.setSource("WHO");
        e.setEventType("HEALTH");
        e.setEventName("WHO outbreak signals (latest)");

        // If you changed WhoResult to include recentCount:
        // store recentCount as currentValue so it reflects "recent outbreaks"
        double value = (r.recentCount > 0) ? r.recentCount : r.count;

        e.setCurrentValue(value);
        e.setUnit("items (recent)");
        e.setSeverity(severityFromWho(r));

        StringBuilder sb = new StringBuilder();
        sb.append("WHO Disease Outbreak News (recent) = ").append((int) value).append("\n");

        if (r.titles != null && !r.titles.isEmpty()) {
            sb.append("\nLatest titles:\n");
            for (String t : r.titles) {
                sb.append("- ").append(t).append("\n");
            }
        } else {
            sb.append("\nNo recent outbreak titles available.\n");
        }

        e.setDescription(sb.toString());

        // âœ… active only if recent signals exist
        e.setActive(r.recentCount > 0);

        return e;
    }

    private Severity severityFromCount(int count) {
        if (count >= 30) return Severity.CRITICAL;
        if (count >= 15) return Severity.DANGER;
        if (count >= 5)  return Severity.WARNING;
        return Severity.INFO;
    }

    // âœ… ONLY ONE method now
    private Severity severityFromWho(WhoClient.WhoResult r) {

        // Preferred: recentCount logic
        if (r.recentCount <= 0 || r.titles == null || r.titles.isEmpty()) {
            return Severity.INFO;
        }

        // Escalation by recent volume
        if (r.recentCount >= 10) return Severity.DANGER;
        if (r.recentCount >= 5)  return Severity.WARNING;

        // Escalation by keywords (optional)
        Severity s = Severity.INFO;
        for (String t : r.titles) {
            String x = t.toLowerCase();
            if (x.contains("fatal") || x.contains("deaths") || x.contains("emergency")) {
                s = Severity.DANGER;
            }
        }
        return s;
    }
}
