package com.advisora.Model;

import java.util.ArrayList;
import java.util.List;

public class ProjectAcceptanceEstimate {
    private int projectId;
    private int scorePercent;

    private double tType;
    private double tClient;
    private double sBudget;
    private double sDossier;

    private double contribType;
    private double contribClient;
    private double contribBudget;
    private double contribDossier;

    private boolean lowConfidence;
    private List<String> reasons = new ArrayList<>();

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public int getScorePercent() {
        return scorePercent;
    }

    public void setScorePercent(int scorePercent) {
        this.scorePercent = scorePercent;
    }

    public double gettType() {
        return tType;
    }

    public void settType(double tType) {
        this.tType = tType;
    }

    public double gettClient() {
        return tClient;
    }

    public void settClient(double tClient) {
        this.tClient = tClient;
    }

    public double getsBudget() {
        return sBudget;
    }

    public void setsBudget(double sBudget) {
        this.sBudget = sBudget;
    }

    public double getsDossier() {
        return sDossier;
    }

    public void setsDossier(double sDossier) {
        this.sDossier = sDossier;
    }

    public double getContribType() {
        return contribType;
    }

    public void setContribType(double contribType) {
        this.contribType = contribType;
    }

    public double getContribClient() {
        return contribClient;
    }

    public void setContribClient(double contribClient) {
        this.contribClient = contribClient;
    }

    public double getContribBudget() {
        return contribBudget;
    }

    public void setContribBudget(double contribBudget) {
        this.contribBudget = contribBudget;
    }

    public double getContribDossier() {
        return contribDossier;
    }

    public void setContribDossier(double contribDossier) {
        this.contribDossier = contribDossier;
    }

    public boolean isLowConfidence() {
        return lowConfidence;
    }

    public void setLowConfidence(boolean lowConfidence) {
        this.lowConfidence = lowConfidence;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
