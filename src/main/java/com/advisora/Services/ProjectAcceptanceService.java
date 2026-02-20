package com.advisora.Services;

import com.advisora.Model.Project;
import com.advisora.Model.ProjectAcceptanceEstimate;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProjectAcceptanceService {
    private static final double W_TYPE = 0.45;
    private static final double W_CLIENT = 0.20;
    private static final double W_BUDGET = 0.20;
    private static final double W_DOSSIER = 0.15;

    private static final double M_TYPE = 30.0;
    private static final double M_CLIENT = 20.0;
    private static final double K_BUDGET = 1.0;

    public ProjectAcceptanceEstimate estimateFor(Project project) {
        Map<Integer, ProjectAcceptanceEstimate> map = estimateForPending(List.of(project));
        return map.get(project.getIdProj());
    }

    public Map<Integer, ProjectAcceptanceEstimate> estimateForPending(List<Project> projects) {
        Map<Integer, ProjectAcceptanceEstimate> result = new HashMap<>();
        if (projects == null || projects.isEmpty()) {
            return result;
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            GlobalStats global = loadGlobalStats(cnx);
            Map<String, CountStats> typeStats = loadTypeStats(cnx);
            Map<Integer, CountStats> clientStats = loadClientStats(cnx);
            Map<String, Double> medianByType = loadAcceptedMedianByType(cnx);
            double globalMedian = loadAcceptedGlobalMedian(cnx);

            for (Project p : projects) {
                ProjectAcceptanceEstimate e = computeEstimate(p, global, typeStats, clientStats, medianByType, globalMedian);
                result.put(p.getIdProj(), e);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur calcul estimation acceptation: " + ex.getMessage(), ex);
        }

        return result;
    }

    private ProjectAcceptanceEstimate computeEstimate(Project p,
                                                      GlobalStats global,
                                                      Map<String, CountStats> typeStats,
                                                      Map<Integer, CountStats> clientStats,
                                                      Map<String, Double> medianByType,
                                                      double globalMedian) {
        String type = normType(p.getTypeProj());
        CountStats typeCount = typeStats.getOrDefault(type, new CountStats());
        CountStats clientCount = clientStats.getOrDefault(p.getIdClient(), new CountStats());

        double rG = global.total == 0 ? 0.5 : (double) global.accepted / global.total;

        double rType = typeCount.total == 0 ? rG : (double) typeCount.accepted / typeCount.total;
        double tType = ((typeCount.total * rType) + (M_TYPE * rG)) / (typeCount.total + M_TYPE);

        double rClient = clientCount.total == 0 ? rG : (double) clientCount.accepted / clientCount.total;
        double tClient = ((clientCount.total * rClient) + (M_CLIENT * rG)) / (clientCount.total + M_CLIENT);

        double median = medianByType.getOrDefault(type, globalMedian);
        double sBudget = budgetScore(p.getBudgetProj(), median);

        double sDossier = dossierScore(p);

        double prob = clamp01((W_TYPE * tType) + (W_CLIENT * tClient) + (W_BUDGET * sBudget) + (W_DOSSIER * sDossier));

        ProjectAcceptanceEstimate out = new ProjectAcceptanceEstimate();
        out.setProjectId(p.getIdProj());
        out.setScorePercent((int) Math.round(prob * 100.0));

        out.settType(tType);
        out.settClient(tClient);
        out.setsBudget(sBudget);
        out.setsDossier(sDossier);

        out.setContribType(W_TYPE * tType);
        out.setContribClient(W_CLIENT * tClient);
        out.setContribBudget(W_BUDGET * sBudget);
        out.setContribDossier(W_DOSSIER * sDossier);

        boolean lowConfidence = typeCount.total < 5 && clientCount.total < 3;
        out.setLowConfidence(lowConfidence);

        List<String> reasons = new ArrayList<>();
        reasons.add("Historique type: " + toPct(tType));
        reasons.add("Historique client: " + toPct(tClient));
        reasons.add("Budget: " + toPct(sBudget));
        reasons.add("Dossier: " + toPct(sDossier));
        if (lowConfidence) {
            reasons.add("Faible confiance (peu d'historique)");
        }
        out.setReasons(reasons);

        return out;
    }

    private String toPct(double v) {
        return String.format(Locale.US, "%.0f%%", (v * 100.0));
    }

    private double budgetScore(double budget, double median) {
        if (budget <= 0) return 0.2;
        if (median <= 0) return 0.5;

        double ratio = budget / median;
        if (ratio <= 0) return 0.2;

        double score = Math.exp(-Math.abs(Math.log(ratio)) / K_BUDGET);
        return clamp01(score);
    }

    private double dossierScore(Project p) {
        double score = 0.0;
        if (p.getTitleProj() != null && !p.getTitleProj().isBlank()) score += 0.25;
        if (p.getDescriptionProj() != null && p.getDescriptionProj().trim().length() >= 30) score += 0.35;
        if (p.getTypeProj() != null && !p.getTypeProj().isBlank()) score += 0.15;
        if (p.getBudgetProj() > 0) score += 0.25;
        return clamp01(score);
    }

    private double clamp01(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }

    private GlobalStats loadGlobalStats(Connection cnx) throws SQLException {
        String sql = "SELECT " +
                "SUM(CASE WHEN stateProj='ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "COUNT(*) AS total " +
                "FROM projects WHERE stateProj IN ('ACCEPTED','REFUSED')";

        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            GlobalStats stats = new GlobalStats();
            if (rs.next()) {
                stats.accepted = rs.getInt("accepted");
                stats.total = rs.getInt("total");
            }
            return stats;
        }
    }

    private Map<String, CountStats> loadTypeStats(Connection cnx) throws SQLException {
        String sql = "SELECT UPPER(TRIM(COALESCE(typeProj,''))) AS t, " +
                "SUM(CASE WHEN stateProj='ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "COUNT(*) AS total " +
                "FROM projects " +
                "WHERE stateProj IN ('ACCEPTED','REFUSED') " +
                "GROUP BY UPPER(TRIM(COALESCE(typeProj,'')))";

        Map<String, CountStats> out = new HashMap<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CountStats s = new CountStats();
                s.accepted = rs.getInt("accepted");
                s.total = rs.getInt("total");
                out.put(normType(rs.getString("t")), s);
            }
        }
        return out;
    }

    private Map<Integer, CountStats> loadClientStats(Connection cnx) throws SQLException {
        String sql = "SELECT idClient, " +
                "SUM(CASE WHEN stateProj='ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "COUNT(*) AS total " +
                "FROM projects " +
                "WHERE stateProj IN ('ACCEPTED','REFUSED') " +
                "GROUP BY idClient";

        Map<Integer, CountStats> out = new HashMap<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CountStats s = new CountStats();
                s.accepted = rs.getInt("accepted");
                s.total = rs.getInt("total");
                out.put(rs.getInt("idClient"), s);
            }
        }
        return out;
    }

    private Map<String, Double> loadAcceptedMedianByType(Connection cnx) throws SQLException {
        String sql = "SELECT UPPER(TRIM(COALESCE(typeProj,''))) AS t, budgetProj " +
                "FROM projects WHERE stateProj='ACCEPTED' AND budgetProj > 0 " +
                "ORDER BY t, budgetProj";

        Map<String, List<Double>> grouped = new HashMap<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String t = normType(rs.getString("t"));
                grouped.computeIfAbsent(t, k -> new ArrayList<>()).add(rs.getDouble("budgetProj"));
            }
        }

        Map<String, Double> med = new HashMap<>();
        for (Map.Entry<String, List<Double>> e : grouped.entrySet()) {
            med.put(e.getKey(), median(e.getValue()));
        }
        return med;
    }

    private double loadAcceptedGlobalMedian(Connection cnx) throws SQLException {
        String sql = "SELECT budgetProj FROM projects WHERE stateProj='ACCEPTED' AND budgetProj > 0 ORDER BY budgetProj";
        List<Double> values = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) values.add(rs.getDouble(1));
        }
        if (values.isEmpty()) return 1.0;
        return median(values);
    }

    private double median(List<Double> list) {
        if (list == null || list.isEmpty()) return 0.0;
        Collections.sort(list);
        int n = list.size();
        int mid = n / 2;
        if ((n % 2) == 1) return list.get(mid);
        return (list.get(mid - 1) + list.get(mid)) / 2.0;
    }

    private String normType(String type) {
        if (type == null) return "";
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private static class CountStats {
        int accepted;
        int total;
    }

    private static class GlobalStats {
        int accepted;
        int total;
    }
}
