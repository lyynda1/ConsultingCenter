package com.advisora.Services.investment;

import com.advisora.Model.investment.MacroAnalysis;
import com.advisora.Model.investment.MacroAnalysis.RiskLevel;
import com.advisora.Model.investment.MacroIndicators;

/**
 * Pure computation engine â€” no I/O.
 * Computes a Macro Risk Score and an Adjusted ROI from MacroIndicators.
 *
 * Score formula (0..100):
 * score = inflation*3 + lendingRate*2 + max(0, (3 - gdpGrowth))*5
 * score = clamp(score, 0, 100)
 *
 * Risk levels:
 * 0..33 â†’ LOW
 * 34..66 â†’ MEDIUM
 * 67..100â†’ HIGH
 *
 * Adjusted ROI (gross ROI assumed = 12%):
 * adjustedROI = grossROI âˆ’ inflation âˆ’ riskPremium
 * riskPremium: LOW=1.5, MEDIUM=3.0, HIGH=5.0
 */
public class MacroRiskEngine {

    private static final double GROSS_ROI = 12.0; // %

    public MacroAnalysis analyse(MacroIndicators m) {
        double raw = m.getInflation() * 3.0
                + m.getLendingRate() * 2.0
                + Math.max(0, (3.0 - m.getGdpGrowth())) * 5.0;

        double score = Math.max(0, Math.min(100, raw));

        RiskLevel level;
        if (score <= 33)
            level = RiskLevel.LOW;
        else if (score <= 66)
            level = RiskLevel.MEDIUM;
        else
            level = RiskLevel.HIGH;

        double riskPremium = switch (level) {
            case LOW -> 1.5;
            case MEDIUM -> 3.0;
            case HIGH -> 5.0;
        };

        double adjustedROI = GROSS_ROI - m.getInflation() - riskPremium;

        return new MacroAnalysis(m, score, level, GROSS_ROI, adjustedROI, riskPremium);
    }
}


