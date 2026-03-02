package com.advisora.Model.projet;

public class ProjectTypeStat {
    private String type;
    private int total;
    private int accepted;
    private double acceptanceRatePercent;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getAccepted() {
        return accepted;
    }

    public void setAccepted(int accepted) {
        this.accepted = accepted;
    }

    public double getAcceptanceRatePercent() {
        return acceptanceRatePercent;
    }

    public void setAcceptanceRatePercent(double acceptanceRatePercent) {
        this.acceptanceRatePercent = acceptanceRatePercent;
    }
}

