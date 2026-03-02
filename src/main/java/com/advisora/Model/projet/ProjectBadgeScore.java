package com.advisora.Model.projet;

public class ProjectBadgeScore {
    private int projectId;
    private double pbs;
    private String badge;
    private double temporalScore;
    private double reliabilityScore;
    private double regularityScore;
    private double stabilityScore;
    private boolean hadRefusalHistory;

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public double getPbs() {
        return pbs;
    }

    public void setPbs(double pbs) {
        this.pbs = pbs;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public double getTemporalScore() {
        return temporalScore;
    }

    public void setTemporalScore(double temporalScore) {
        this.temporalScore = temporalScore;
    }

    public double getReliabilityScore() {
        return reliabilityScore;
    }

    public void setReliabilityScore(double reliabilityScore) {
        this.reliabilityScore = reliabilityScore;
    }

    public double getRegularityScore() {
        return regularityScore;
    }

    public void setRegularityScore(double regularityScore) {
        this.regularityScore = regularityScore;
    }

    public double getStabilityScore() {
        return stabilityScore;
    }

    public void setStabilityScore(double stabilityScore) {
        this.stabilityScore = stabilityScore;
    }

    public boolean isHadRefusalHistory() {
        return hadRefusalHistory;
    }

    public void setHadRefusalHistory(boolean hadRefusalHistory) {
        this.hadRefusalHistory = hadRefusalHistory;
    }
}

