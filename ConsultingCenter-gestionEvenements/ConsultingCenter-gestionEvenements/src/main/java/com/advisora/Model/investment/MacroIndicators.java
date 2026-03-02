package com.advisora.Model.investment;

/**
 * Holds raw macroeconomic indicators fetched from the World Bank API for
 * Tunisia.
 * lendingEstimated=true means the lending rate came from BCT fallback (not
 * World Bank live data).
 */
public class MacroIndicators {

    private final double inflation; // FP.CPI.TOTL.ZG — annual CPI %
    private final double lendingRate; // FR.INR.LEND or BCT key rate fallback
    private final double gdpGrowth; // NY.GDP.MKTP.KD.ZG — GDP growth annual %
    private final int year; // most recent year available for live indicators
    private final boolean lendingEstimated; // true when lending rate is from BCT, not World Bank

    public MacroIndicators(double inflation, double lendingRate, double gdpGrowth,
            int year, boolean lendingEstimated) {
        this.inflation = inflation;
        this.lendingRate = lendingRate;
        this.gdpGrowth = gdpGrowth;
        this.year = year;
        this.lendingEstimated = lendingEstimated;
    }

    public double getInflation() {
        return inflation;
    }

    public double getLendingRate() {
        return lendingRate;
    }

    public double getGdpGrowth() {
        return gdpGrowth;
    }

    public int getYear() {
        return year;
    }

    public boolean isLendingEstimated() {
        return lendingEstimated;
    }

    @Override
    public String toString() {
        return String.format(
                "MacroIndicators{year=%d, inflation=%.2f%%, lending=%.2f%%%s, gdp=%.2f%%}",
                year, inflation, lendingRate, lendingEstimated ? " [BCT est.]" : "", gdpGrowth);
    }
}

