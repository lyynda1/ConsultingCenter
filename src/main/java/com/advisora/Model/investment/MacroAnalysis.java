package com.advisora.Model.investment;

/**
 * Computed macro-economic analysis result for an investment context.
 */
public class MacroAnalysis {

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    private final MacroIndicators data;
    private final double score; // 0..100
    private final RiskLevel riskLevel;
    private final double adjustedROI; // %
    private final double riskPremium; // %
    private final double grossROI; // always 12.0 (assumption)

    public MacroAnalysis(MacroIndicators data,
            double score,
            RiskLevel riskLevel,
            double grossROI,
            double adjustedROI,
            double riskPremium) {
        this.data = data;
        this.score = score;
        this.riskLevel = riskLevel;
        this.grossROI = grossROI;
        this.adjustedROI = adjustedROI;
        this.riskPremium = riskPremium;
    }

    public MacroIndicators getData() {
        return data;
    }

    public double getScore() {
        return score;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public double getAdjustedROI() {
        return adjustedROI;
    }

    public double getRiskPremium() {
        return riskPremium;
    }

    public double getGrossROI() {
        return grossROI;
    }
}


