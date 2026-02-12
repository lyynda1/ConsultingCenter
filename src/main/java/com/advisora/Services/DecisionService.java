/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Service layer: business logic and SQL orchestration
*/
package com.advisora.Services;

import com.advisora.Model.Decision;
import com.advisora.enums.DecisionStatus;
import com.advisora.enums.ProjectStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DecisionService {
    // Create a new manager decision.
    // Insert + project status sync are done in one DB transaction.
    public void add(Decision d) {
        validate(d, true);
        if (d.getDateDecision() == null) d.setDateDecision(LocalDateTime.now());
        run(() -> {
            String insertSql = "INSERT INTO Decisions (StatutD, descriptionD, dateDecision, idProj, idUser) VALUES (?, ?, ?, ?, ?)";
            Connection cnx = MyConnection.getInstance().getConnection();
            boolean old = cnx.getAutoCommit();
            try {
                cnx.setAutoCommit(false);
                try (PreparedStatement ps = cnx.prepareStatement(insertSql)) {
                    ps.setString(1, toDbDecisionStatus(d.getStatutD()));
                    ps.setString(2, d.getDescriptionD());
                    ps.setTimestamp(3, Timestamp.valueOf(d.getDateDecision()));
                    ps.setInt(4, d.getIdProj());
                    ps.setInt(5, d.getIdUser());
                    ps.executeUpdate();
                }
                syncProjectStatus(cnx, d.getIdProj(), d.getStatutD());
                cnx.commit();
            } catch (SQLException e) {
                cnx.rollback();
                throw e;
            } finally {
                cnx.setAutoCommit(old);
            }
        });
    }

    // Update an existing decision and re-apply project status synchronization.
    public void update(Decision d) {
        validate(d, false);
        if (d.getDateDecision() == null) d.setDateDecision(LocalDateTime.now());
        run(() -> {
            String updateSql = "UPDATE Decisions SET StatutD=?, descriptionD=?, dateDecision=?, idProj=?, idUser=? WHERE idD=?";
            Connection cnx = MyConnection.getInstance().getConnection();
            boolean old = cnx.getAutoCommit();
            try {
                cnx.setAutoCommit(false);
                try (PreparedStatement ps = cnx.prepareStatement(updateSql)) {
                    ps.setString(1, toDbDecisionStatus(d.getStatutD()));
                    ps.setString(2, d.getDescriptionD());
                    ps.setTimestamp(3, Timestamp.valueOf(d.getDateDecision()));
                    ps.setInt(4, d.getIdProj());
                    ps.setInt(5, d.getIdUser());
                    ps.setInt(6, d.getIdD());
                    ps.executeUpdate();
                }
                syncProjectStatus(cnx, d.getIdProj(), d.getStatutD());
                cnx.commit();
            } catch (SQLException e) {
                cnx.rollback();
                throw e;
            } finally {
                cnx.setAutoCommit(old);
            }
        });
    }

    // Delete one decision by id.
    public void delete(int idDecision) {
        run(() -> {
            String sql = "DELETE FROM Decisions WHERE idD=?";
            try (Connection cnx = MyConnection.getInstance().getConnection(); PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, idDecision);
                ps.executeUpdate();
            }
        });
    }

    // Read all decisions ordered by newest date.
    public List<Decision> getAll() {
        return call(() -> {
            String sql = "SELECT * FROM Decisions ORDER BY dateDecision DESC";
            List<Decision> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection(); PreparedStatement ps = cnx.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        });
    }

    // Read decisions linked to a specific project.
    public List<Decision> getByProject(int idProj) {
        return call(() -> {
            String sql = "SELECT * FROM Decisions WHERE idProj=? ORDER BY dateDecision DESC";
            List<Decision> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection(); PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, idProj);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                }
            }
            return list;
        });
    }

    // Helper for manager flow: force idUser from authenticated session.
    public void addForCurrentManager(Decision d) {
        d.setIdUser(SessionContext.getCurrentUserId());
        add(d);
    }

    // Validate decision payload before SQL execution.
    private void validate(Decision d, boolean create) {
        if (d == null) throw new IllegalArgumentException("Decision null");
        if (!create && d.getIdD() <= 0) throw new IllegalArgumentException("idD invalide");
        if (d.getStatutD() == null) throw new IllegalArgumentException("StatutD requis");
        if (d.getDescriptionD() == null || d.getDescriptionD().isBlank()) throw new IllegalArgumentException("Description requise");
        if (d.getIdProj() <= 0) throw new IllegalArgumentException("idProj invalide");
        if (d.getIdUser() <= 0) throw new IllegalArgumentException("idUser invalide");
    }

    private void run(SqlRun r) {
        try { r.exec(); } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private <T> T call(SqlCall<T> c) {
        try { return c.exec(); } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // Core business rule:
    // ACTIVE -> ACCEPTED, REFUSED -> REFUSED, PENDING -> PENDING.
    // This keeps Project state consistent with latest manager decision.
    private void syncProjectStatus(Connection cnx, int idProj, DecisionStatus decisionStatus) throws SQLException {
        ProjectStatus newStatus;
        if (decisionStatus == DecisionStatus.ACTIVE) {
            newStatus = ProjectStatus.ACCEPTED;
        } else if (decisionStatus == DecisionStatus.REFUSED) {
            newStatus = ProjectStatus.REFUSED;
        } else {
            newStatus = ProjectStatus.PENDING;
        }
        String sql = "UPDATE Projects SET stateProj=?, updatedAtProj=NOW() WHERE idProj=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setInt(2, idProj);
            ps.executeUpdate();
        }
    }

    // Convert one SQL row to Decision domain object.
    private Decision map(ResultSet rs) throws SQLException {
        Decision d = new Decision();
        d.setIdD(rs.getInt("idD"));
        d.setStatutD(fromDbDecisionStatus(rs.getString("StatutD")));
        d.setDescriptionD(rs.getString("descriptionD"));
        Timestamp ts = rs.getTimestamp("dateDecision");
        d.setDateDecision(ts == null ? null : ts.toLocalDateTime());
        d.setIdProj(rs.getInt("idProj"));
        d.setIdUser(rs.getInt("idUser"));
        return d;
    }

    private String toDbDecisionStatus(DecisionStatus s) {
        if (s == null) {
            return "pending";
        }
        return s.name().toLowerCase();
    }

    // Defensive parser for DB strings (lower/upper case tolerant).
    private DecisionStatus fromDbDecisionStatus(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            return DecisionStatus.PENDING;
        }
        try {
            return DecisionStatus.valueOf(dbValue.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DecisionStatus.PENDING;
        }
    }

    @FunctionalInterface private interface SqlRun { void exec() throws SQLException; }
    @FunctionalInterface private interface SqlCall<T> { T exec() throws SQLException; }
}
