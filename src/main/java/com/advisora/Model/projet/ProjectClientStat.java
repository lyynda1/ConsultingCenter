package com.advisora.Model.projet;

public class ProjectClientStat {
    private int clientId;
    private String clientName;
    private int total;
    private int accepted;
    private double acceptanceRatePercent;

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
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

