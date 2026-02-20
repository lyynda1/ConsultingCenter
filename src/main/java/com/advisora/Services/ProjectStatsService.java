package com.advisora.Services;

import com.advisora.Model.Project;
import com.advisora.Model.ProjectClientStat;
import com.advisora.Model.ProjectDashboardData;
import com.advisora.Model.ProjectStatsSummary;
import com.advisora.Model.ProjectTypeStat;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProjectStatsService {
    private final ProjectService projectService = new ProjectService();

    public ProjectDashboardData getForClient(int clientId) {
        ProjectDashboardData out = new ProjectDashboardData();
        out.setSummary(loadSummaryForClient(clientId));
        out.setTypeStats(loadTypeStatsForClient(clientId));
        out.setClientStats(new ArrayList<>());
        return out;
    }

    public ProjectDashboardData getForManager() {
        ProjectDashboardData out = new ProjectDashboardData();
        out.setSummary(loadSummaryGlobal());
        out.setTypeStats(loadTypeStatsGlobal());
        out.setClientStats(loadClientStatsGlobal());
        return out;
    }

    private ProjectStatsSummary loadSummaryForClient(int clientId) {
        String sql = "SELECT " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'PENDING' THEN 1 ELSE 0 END) AS pending, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'REFUSED' THEN 1 ELSE 0 END) AS refused " +
                "FROM projects WHERE idClient = ?";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                ProjectStatsSummary summary = new ProjectStatsSummary();
                if (rs.next()) {
                    summary.setTotal(rs.getInt("total"));
                    summary.setPending(rs.getInt("pending"));
                    summary.setAccepted(rs.getInt("accepted"));
                    summary.setRefused(rs.getInt("refused"));
                    summary.setAcceptanceRatePercent(rate(summary.getAccepted(), summary.getRefused()));
                }
                summary.setAvgProgressPercent(avgProgress(projectService.getByClient(clientId)));
                return summary;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur stats client projets: " + e.getMessage(), e);
        }
    }

    private ProjectStatsSummary loadSummaryGlobal() {
        String sql = "SELECT " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'PENDING' THEN 1 ELSE 0 END) AS pending, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'REFUSED' THEN 1 ELSE 0 END) AS refused " +
                "FROM projects";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ProjectStatsSummary summary = new ProjectStatsSummary();
            if (rs.next()) {
                summary.setTotal(rs.getInt("total"));
                summary.setPending(rs.getInt("pending"));
                summary.setAccepted(rs.getInt("accepted"));
                summary.setRefused(rs.getInt("refused"));
                summary.setAcceptanceRatePercent(rate(summary.getAccepted(), summary.getRefused()));
            }
            summary.setAvgProgressPercent(avgProgress(projectService.getAll()));
            return summary;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur stats globales projets: " + e.getMessage(), e);
        }
    }

    private List<ProjectTypeStat> loadTypeStatsForClient(int clientId) {
        String sql = "SELECT COALESCE(NULLIF(TRIM(typeProj), ''), '-') AS type, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'REFUSED' THEN 1 ELSE 0 END) AS refused " +
                "FROM projects WHERE idClient = ? " +
                "GROUP BY COALESCE(NULLIF(TRIM(typeProj), ''), '-') " +
                "ORDER BY total DESC, type ASC";
        return queryTypeStats(sql, ps -> ps.setInt(1, clientId));
    }

    private List<ProjectTypeStat> loadTypeStatsGlobal() {
        String sql = "SELECT COALESCE(NULLIF(TRIM(typeProj), ''), '-') AS type, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "SUM(CASE WHEN UPPER(stateProj) = 'REFUSED' THEN 1 ELSE 0 END) AS refused " +
                "FROM projects " +
                "GROUP BY COALESCE(NULLIF(TRIM(typeProj), ''), '-') " +
                "ORDER BY total DESC, type ASC";
        return queryTypeStats(sql, null);
    }

    private List<ProjectClientStat> loadClientStatsGlobal() {
        String sql = "SELECT p.idClient, " +
                "COALESCE(NULLIF(TRIM(CONCAT(COALESCE(u.prenomUser, ''), ' ', COALESCE(u.nomUser, ''))), ''), CONCAT('Client #', p.idClient)) AS clientName, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN UPPER(p.stateProj) = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted, " +
                "SUM(CASE WHEN UPPER(p.stateProj) = 'REFUSED' THEN 1 ELSE 0 END) AS refused " +
                "FROM projects p " +
                "LEFT JOIN `user` u ON u.idUser = p.idClient " +
                "GROUP BY p.idClient, clientName " +
                "ORDER BY total DESC, clientName ASC";

        List<ProjectClientStat> out = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ProjectClientStat row = new ProjectClientStat();
                row.setClientId(rs.getInt("idClient"));
                row.setClientName(rs.getString("clientName"));
                row.setTotal(rs.getInt("total"));
                row.setAccepted(rs.getInt("accepted"));
                row.setAcceptanceRatePercent(rate(row.getAccepted(), rs.getInt("refused")));
                out.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur stats clients projets: " + e.getMessage(), e);
        }
        return out;
    }

    private List<ProjectTypeStat> queryTypeStats(String sql, SqlSetter setter) {
        List<ProjectTypeStat> out = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            if (setter != null) {
                setter.set(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProjectTypeStat row = new ProjectTypeStat();
                    row.setType(rs.getString("type"));
                    row.setTotal(rs.getInt("total"));
                    row.setAccepted(rs.getInt("accepted"));
                    row.setAcceptanceRatePercent(rate(row.getAccepted(), rs.getInt("refused")));
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur stats type projets: " + e.getMessage(), e);
        }
        return out;
    }

    private double avgProgress(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (Project p : projects) {
            if (p != null && p.getStateProj() != null && "ACCEPTED".equals(p.getStateProj().name())) {
                sum += clampPercent(p.getAvancementProj());
                count++;
            }
        }
        return count == 0 ? 0.0 : (sum / count);
    }

    private double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double rate(int accepted, int refused) {
        int den = accepted + refused;
        if (den <= 0) {
            return 0.0;
        }
        return (accepted * 100.0) / den;
    }

    @FunctionalInterface
    private interface SqlSetter {
        void set(PreparedStatement ps) throws SQLException;
    }
}
