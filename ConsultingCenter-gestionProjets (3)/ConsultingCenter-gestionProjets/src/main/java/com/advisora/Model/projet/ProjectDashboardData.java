package com.advisora.Model.projet;

import java.util.ArrayList;
import java.util.List;

public class ProjectDashboardData {
    private ProjectStatsSummary summary = new ProjectStatsSummary();
    private List<ProjectTypeStat> typeStats = new ArrayList<>();
    private List<ProjectClientStat> clientStats = new ArrayList<>();

    public ProjectStatsSummary getSummary() {
        return summary;
    }

    public void setSummary(ProjectStatsSummary summary) {
        this.summary = summary;
    }

    public List<ProjectTypeStat> getTypeStats() {
        return typeStats;
    }

    public void setTypeStats(List<ProjectTypeStat> typeStats) {
        this.typeStats = typeStats;
    }

    public List<ProjectClientStat> getClientStats() {
        return clientStats;
    }

    public void setClientStats(List<ProjectClientStat> clientStats) {
        this.clientStats = clientStats;
    }
}
