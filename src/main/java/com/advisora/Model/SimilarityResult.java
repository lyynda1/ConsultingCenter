package com.advisora.Model;

public class SimilarityResult {
    private final boolean duplicate;
    private final double bestScore;
    private final int bestMatchingId;
    private final String bestMatchName;

    public SimilarityResult(boolean duplicate, double bestScore, int bestMatchingId, String bestMatchName) {
        this.duplicate = duplicate;
        this.bestScore = bestScore;
        this.bestMatchingId = bestMatchingId;
        this.bestMatchName = bestMatchName;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public double getBestScore() {
        return bestScore;
    }

    public int getBestMatchingId() {
        return bestMatchingId;
    }

    public String getBestMatchName() {
        return bestMatchName;
    }

    public String toPercentString() {
        return String.format(java.util.Locale.US, "%.0f%%", bestScore * 100.0);
    }


}
