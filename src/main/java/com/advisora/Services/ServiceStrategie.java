package com.advisora.Services;

import com.advisora.Model.Notification;
import com.advisora.Model.Project;
import com.advisora.Model.Strategie;
import com.advisora.enums.StrategyStatut;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ServiceStrategie implements IService<Strategie> {

    @Override
    public void ajouter(Strategie strategie) {
        validate(strategie, true);
        String sql = "INSERT INTO strategies (versions, statusStrategie, CreatedAtS, lockedAt, news, idProj, idUser, nomStrategie) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, strategie.getVersion());
            ps.setString(2, "En_cours"); // default status for new strategy
            ps.setTimestamp(3, Timestamp.valueOf(strategie.getCreatedAt()));
            ps.setTimestamp(4, strategie.getLockedAt() == null ? null : Timestamp.valueOf(strategie.getLockedAt()));
            ps.setString(5, emptyToNull(strategie.getNews()));
            ps.setInt(6, strategie.getProjet().getIdProj());
            if (strategie.getIdUser() == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, strategie.getIdUser());
            }
            ps.setString(8, strategie.getNomStrategie().trim());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    strategie.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout strategie: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Strategie> afficher() {
        String sql = "SELECT s.idStrategie, s.versions, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.news, " +
                "s.idProj, s.idUser, s.nomStrategie, s.justification, " +
                "p.titleProj " +
                "FROM strategies s " +
                "LEFT JOIN projects p ON p.idProj = s.idProj " +
                "ORDER BY s.idStrategie DESC";

        List<Strategie> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategies: " + e.getMessage(), e);
        }
    }

    @Override
    public void modifier(Strategie strategie) {
        validate(strategie, false);

        Integer newProjId = null;
        if (strategie.getProjet() != null && strategie.getProjet().getIdProj() > 0) {
            newProjId = strategie.getProjet().getIdProj();
        }

        Integer oldProjId = getCurrentProjectId(strategie.getId());

        StrategyStatut statusToSave = strategie.getStatut();
        String justification = strategie.getJustification();

        if (!Objects.equals(oldProjId, newProjId)) {
            statusToSave = StrategyStatut.EN_COURS;
            justification = null;              // clear justification if project changed
            strategie.setStatut(statusToSave);
        }

        String sql = "UPDATE strategies SET versions=?, statusStrategie=?, lockedAt=?, idProj=?, idUser=?, nomStrategie=?, justification=? " +
                "WHERE idStrategie=?";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.setInt(1, strategie.getVersion());
            ps.setString(2, resolveDbStrategyStatus(cnx, statusToSave));
            ps.setTimestamp(3, strategie.getLockedAt() == null ? null : Timestamp.valueOf(strategie.getLockedAt()));

            // 4) idProj
            if (newProjId == null) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, newProjId);

            // 5) idUser
            if (strategie.getIdUser() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, strategie.getIdUser());

            // 6) nomStrategie
            ps.setString(6, strategie.getNomStrategie().trim());

            // 7) justification
            ps.setString(7, (justification == null || justification.isBlank()) ? null : justification.trim());

            // 8) idStrategie (WHERE)
            ps.setInt(8, strategie.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification strategie: " + e.getMessage(), e);
        }
    }



    @Override
    public void supprimer(Strategie strategie) {
        if (strategie == null || strategie.getId() <= 0) {
            throw new IllegalArgumentException("Strategie invalide.");
        }
        String sql = "DELETE FROM strategies WHERE idStrategie=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, strategie.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression strategie: " + e.getMessage(), e);
        }
    }

    public Strategie getById(int idStrategie) {
        String sql = "SELECT s.idStrategie, s.versions, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.news, " +
                "s.idProj, s.idUser, s.nomStrategie, s.justification, " +
                "p.titleProj " +
                "FROM strategies s " +
                "LEFT JOIN projects p ON p.idProj = s.idProj " +
                "WHERE s.idStrategie=?";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idStrategie);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategie: " + e.getMessage(), e);
        }
    }

    private Strategie map(ResultSet rs) throws SQLException {
        Strategie s = new Strategie();
        s.setId(rs.getInt("idStrategie"));
        s.setVersion(rs.getInt("versions"));
        s.setStatut(StrategyStatut.fromDb(rs.getString("statusStrategie")));
        Timestamp created = rs.getTimestamp("CreatedAtS");
        s.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp locked = rs.getTimestamp("lockedAt");
        s.setLockedAt(locked == null ? null : locked.toLocalDateTime());
        s.setNews(rs.getString("news"));
        s.setNomStrategie(rs.getString("nomStrategie"));
        s.setJustification(rs.getString("justification")); // set justification even if project is null, for potential later use
        int idProj = rs.getInt("idProj");
        if (!rs.wasNull()) {
            Project p = new Project();
            p.setIdProj(idProj);
            p.setTitleProj(rs.getString("titleProj"));
            s.setProjet(p);
        }
        int idUser = rs.getInt("idUser");
        if (!rs.wasNull()) {
            s.setIdUser(idUser);
        }
        return s;
    }

    private void validate(Strategie s, boolean create) {
        if (s == null) throw new IllegalArgumentException("Strategie obligatoire.");
        if (s.getNomStrategie() == null || s.getNomStrategie().isBlank()) throw new IllegalArgumentException("Nom strategie obligatoire.");
        if (s.getProjet() == null || s.getProjet().getIdProj() <= 0) throw new IllegalArgumentException("Projet obligatoire.");
        if (s.getVersion() <= 0) s.setVersion(1);
        if (s.getStatut() == null) s.setStatut(StrategyStatut.EN_COURS);
        if (s.getCreatedAt() == null) s.setCreatedAt(java.time.LocalDateTime.now());
        if (!create && s.getId() <= 0) throw new IllegalArgumentException("idStrategie invalide.");
    }

    private String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    // New DB can contain accented or mojibake enum values for statusStrategie.
    // This method reads the real enum literals from schema and picks the matching one.
    private String resolveDbStrategyStatus(Connection cnx, StrategyStatut status) {
        StrategyStatut safeStatus = status == null ? StrategyStatut.EN_COURS : status;
        String sql = "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='strategies' AND COLUMN_NAME='statusStrategie'";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String columnType = rs.getString(1); // enum('En_cours','Acceptée','Refusée')
                String mapped = mapEnumLiteral(columnType, safeStatus);
                if (mapped != null) {
                    return mapped;
                }
            }
        } catch (SQLException ignored) {
            // fallback below
        }
        return safeStatus.toDb();
    }

    private String mapEnumLiteral(String columnType, StrategyStatut status) {
        if (columnType == null || !columnType.toLowerCase(Locale.ROOT).startsWith("enum(")) {
            return null;
        }
        String raw = columnType.substring(5, columnType.length() - 1); // inside enum(...)
        String[] values = raw.split(",");
        for (String v : values) {
            String literal = v.trim();
            if (literal.startsWith("'") && literal.endsWith("'") && literal.length() >= 2) {
                literal = literal.substring(1, literal.length() - 1);
            }
            String normalized = normalizeLiteral(literal);
            if (status == StrategyStatut.EN_COURS && normalized.contains("EN_COURS")) {
                return literal;
            }
            if (status == StrategyStatut.ACCEPTEE && normalized.contains("ACCEP")) {
                return literal;
            }
            if (status == StrategyStatut.REFUSEE && normalized.contains("REFUS")) {
                return literal;
            }
        }
        return null;
    }

    private String normalizeLiteral(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('-', '_')
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    public List<Strategie> getByProject(int projectId) {
        String sql = """
    SELECT s.idStrategie, s.versions, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.news,
           s.idProj, s.idUser, s.nomStrategie, s.justification,
           p.titleProj
    FROM strategies s
    LEFT JOIN projects p ON p.idProj = s.idProj
    WHERE s.idProj = ?
    ORDER BY s.idStrategie DESC
""";


        List<Strategie> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategies par projet: " + e.getMessage(), e);
        }
    }


    public void applyDecision(int idStrategie, boolean accepted, String justificationIfRefused, boolean detachOnRefuse) {

        if (!accepted) {
            if (justificationIfRefused == null || justificationIfRefused.trim().isEmpty()) {
                throw new IllegalArgumentException("Le refus nécessite une justification.");
            }
        }

        String sql = detachOnRefuse
                ? "UPDATE strategies SET statusStrategie=?, idProj = CASE WHEN ? THEN idProj ELSE NULL END, " +
                "justification = ? WHERE idStrategie=?"
                : "UPDATE strategies SET statusStrategie=?, justification=? WHERE idStrategie=?";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            String status = accepted ? StrategyStatut.ACCEPTEE.toDb() : StrategyStatut.REFUSEE.toDb();
            String justification = accepted ? null : justificationIfRefused.trim();

            if (detachOnRefuse) {
                ps.setString(1, status);
                ps.setBoolean(2, accepted);     // if accepted => keep idProj, else => NULL
                ps.setString(3, justification);
                ps.setInt(4, idStrategie);
            } else {
                ps.setString(1, status);
                ps.setString(2, justification);
                ps.setInt(3, idStrategie);
            }

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour décision strategie: " + e.getMessage(), e);
        }
        NotificationManager.getInstance().addNotification(new Notification("Decision strategie", "Strategie #" + getById(idStrategie).getNomStrategie() + " a été " + (accepted ? "acceptée" : "refusée")));
    }

    public boolean hasActiveStrategyForProject(int projectId) {
        String sql = "SELECT COUNT(*) FROM strategies WHERE idProj = ? AND statusStrategie = ?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setString(2, StrategyStatut.EN_COURS.toDb());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur vérification stratégie active pour projet: " + e.getMessage(), e);
        }
    }

    /** returns current idProj in DB (nullable) */
    private Integer getCurrentProjectId(int idStrategie) {
        String sql = "SELECT idProj FROM strategies WHERE idStrategie=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idStrategie);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int idProj = rs.getInt(1);
                return rs.wasNull() ? null : idProj;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture idProj strategie: " + e.getMessage(), e);
        }
    }



}
