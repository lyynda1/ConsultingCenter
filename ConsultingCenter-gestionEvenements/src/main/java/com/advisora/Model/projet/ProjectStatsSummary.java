package com.advisora.Model.projet;

public class ProjectStatsSummary {
    private int total;
    private int pending;
    private int accepted;
    private int refused;
    private double acceptanceRatePercent;
    private double avgProgressPercent;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getAccepted() {
        return accepted;
    }

    public void setAccepted(int accepted) {
        this.accepted = accepted;
    }

    public int getRefused() {
        return refused;
    }

    public void setRefused(int refused) {
        this.refused = refused;
    }

    public double getAcceptanceRatePercent() {
        return acceptanceRatePercent;
    }

    public void setAcceptanceRatePercent(double acceptanceRatePercent) {
        this.acceptanceRatePercent = acceptanceRatePercent;
    }

    public double getAvgProgressPercent() {
        return avgProgressPercent;
    }

    public void setAvgProgressPercent(double avgProgressPercent) {
        this.avgProgressPercent = avgProgressPercent;
    }
}
