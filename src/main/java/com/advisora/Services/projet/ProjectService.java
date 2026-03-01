/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Service layer: business logic and SQL orchestration
*/
package com.advisora.Services.projet;

import com.advisora.Model.projet.Project;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.ProjectStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProjectService {
    private Boolean taskTableAvailableCache = null;

    public void add(Project p) {
        validate(p, true);
        p.setStateProj(ProjectStatus.PENDING);
        run(() -> {
            String sql = "INSERT INTO Projects (titleProj, descriptionProj, budgetProj, typeProj, stateProj, createdAtProj, updatedAtProj, idClient) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?)";
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setString(1, p.getTitleProj());
                ps.setString(2, p.getDescriptionProj());
                ps.setDouble(3, p.getBudgetProj());
                ps.setString(4, p.getTypeProj());
                ps.setString(5, ProjectStatus.PENDING.name());
                ps.setInt(6, p.getIdClient());
                ps.executeUpdate();
            }
        });
    }

    public void update(Project p) {
        validate(p, false);
        run(() -> {
            String sql = "UPDATE Projects SET titleProj=?, descriptionProj=?, budgetProj=?, typeProj=?, stateProj=?, updatedAtProj=NOW(), idClient=? WHERE idProj=?";
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setString(1, p.getTitleProj());
                ps.setString(2, p.getDescriptionProj());
                ps.setDouble(3, p.getBudgetProj());
                ps.setString(4, p.getTypeProj());
                ps.setString(5, p.getStateProj().name());
                ps.setInt(6, p.getIdClient());
                ps.setInt(7, p.getIdProj());
                ps.executeUpdate();
            }
        });
    }

    public void delete(int idProj) {
        run(() -> {
            String sql = "DELETE FROM Projects WHERE idProj=?";
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, idProj);
                ps.executeUpdate();
            }
        });
    }

    public List<Project> getAll() {
        return call(() -> {
            List<Project> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                String sql = selectClause(cnx) + " ORDER BY p.createdAtProj DESC";
                try (PreparedStatement ps = cnx.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(map(rs));
                }
            }
            return list;
        });
    }

    public List<Project> getByClient(int idClient) {
        return call(() -> {
            List<Project> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                String sql = selectClause(cnx) + " WHERE p.idClient=? ORDER BY p.createdAtProj DESC";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setInt(1, idClient);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) list.add(map(rs));
                    }
                }
            }
            return list;
        });
    }

    public Project getByTitle(String titleProj) {
        if (titleProj == null || titleProj.isBlank()) return null;
        return call(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                String sql = selectClause(cnx) + " WHERE LOWER(TRIM(p.titleProj)) = LOWER(TRIM(?)) LIMIT 1";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setString(1, titleProj);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? map(rs) : null;
                    }
                }
            }
        });
    }

    public Project getByTitleForClient(String titleProj, int idClient) {
        if (titleProj == null || titleProj.isBlank()) return null;
        if (idClient <= 0) return null;
        return call(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                String sql = selectClause(cnx) + " WHERE LOWER(TRIM(p.titleProj)) = LOWER(TRIM(?)) AND p.idClient = ? LIMIT 1";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setString(1, titleProj);
                    ps.setInt(2, idClient);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? map(rs) : null;
                    }
                }
            }
        });
    }

    public Project getById(int idProj) {
        return call(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                String sql = selectClause(cnx) + " WHERE p.idProj=?";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setInt(1, idProj);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? map(rs) : null;
                    }
                }
            }
        });
    }

    public List<Project> getSortedProjects(String sortBy) {
        return call(() -> {
            List<Project> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                String orderBy = switch (sortBy) {
                    case "titleProj" -> "p.titleProj";
                    case "budgetProj" -> "p.budgetProj";
                    case "createdAtProj" -> "p.createdAtProj";
                    case "avancementProj" -> "computedProgress";
                    default -> "p.createdAtProj";
                };

                String sql = selectClause(cnx) + " ORDER BY " + orderBy + " DESC";
                try (PreparedStatement ps = cnx.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(map(rs));
                }
            }
            return list;
        });
    }

    public List<Project> getPendingProjects() {
        return call(() -> {
            List<Project> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                String sql = selectClause(cnx) + " WHERE p.stateProj=? ORDER BY p.createdAtProj ASC";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setString(1, ProjectStatus.PENDING.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) list.add(map(rs));
                    }
                }
            }
            return list;
        });
    }

    public List<Project> getProjectsForCurrentClient() {
        return getByClient(SessionContext.getCurrentUserId());
    }

    private String selectClause(Connection cnx) throws SQLException {
        if (isTaskTableAvailable(cnx)) {
            return "SELECT p.*, COALESCE((" +
                    "SELECT (SUM(CASE WHEN t.status='DONE' THEN t.weight ELSE 0 END) * 100.0) / NULLIF(SUM(t.weight), 0) " +
                    "FROM task t WHERE t.project_id = p.idProj" +
                    "), 0) AS computedProgress FROM Projects p";
        }
        return "SELECT p.*, p.avancementProj AS computedProgress FROM Projects p";
    }

    private boolean isTaskTableAvailable(Connection cnx) throws SQLException {
        if (taskTableAvailableCache != null) {
            return taskTableAvailableCache;
        }
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'task' LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            taskTableAvailableCache = rs.next();
            return taskTableAvailableCache;
        }
    }

    private void validate(Project p, boolean create) {
        if (p == null) throw new IllegalArgumentException("Project null");
        if (p.getTitleProj() == null || p.getTitleProj().isBlank()) throw new IllegalArgumentException("Title required");
        if (p.getBudgetProj() < 0) throw new IllegalArgumentException("Budget >= 0");
        if (p.getIdClient() <= 0) throw new IllegalArgumentException("idClient invalide");
        if (!create && p.getIdProj() <= 0) throw new IllegalArgumentException("idProj invalide");
    }

    private void run(SqlRun r) {
        try {
            r.exec();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T call(SqlCall<T> c) {
        try {
            return c.exec();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Project map(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setIdProj(rs.getInt("idProj"));
        p.setTitleProj(rs.getString("titleProj"));
        p.setDescriptionProj(rs.getString("descriptionProj"));
        p.setBudgetProj(rs.getDouble("budgetProj"));
        p.setTypeProj(rs.getString("typeProj"));
        p.setStateProj(ProjectStatus.fromDb(rs.getString("stateProj")));
        p.setCreatedAtProj(rs.getTimestamp("createdAtProj"));
        p.setUpdatedAtProj(rs.getTimestamp("updatedAtProj"));
        p.setAvancementProj(rs.getDouble("computedProgress"));
        p.setIdClient(rs.getInt("idClient"));
        return p;
    }

    @FunctionalInterface
    private interface SqlRun {
        void exec() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T exec() throws SQLException;
    }
}

