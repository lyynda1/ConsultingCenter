package com.advisora.Services;

import com.advisora.Model.Notification;
import com.advisora.Model.Project;
import com.advisora.Model.SimilarityResult;
import com.advisora.Model.Strategie;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.TypeStrategie;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.*;

public class ServiceStrategie implements IService<Strategie> {

    @Override
    public void ajouter(Strategie strategie) {
        validate(strategie, true);
        java.util.List<Strategie> candidates = getAllByType(strategie.getTypeStrategie());

        // 2) check
        SimilarityResult r = checkUniquenessGlobal(
                strategie,
                null,                // objectives unknown at creation
                candidates,
                null                 // no need to load objective ids
        );

        if (r.isDuplicate()) {
            throw new IllegalArgumentException(
                    "Stratégie déjà existante : \"" + r.getBestMatchName() + "\"\n" +
                            "Similarité: " + r.toPercentString()
            );
        }

        String sql = "INSERT INTO strategies (versions, statusStrategie, CreatedAtS, lockedAt, idProj, idUser, nomStrategie, type, budgetTotal, gainEstime) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, strategie.getVersion());
            ps.setString(2, "En_cours"); // default status for new strategy
            ps.setTimestamp(3, Timestamp.valueOf(strategie.getCreatedAt()));
            ps.setTimestamp(4, strategie.getLockedAt() == null ? null : Timestamp.valueOf(strategie.getLockedAt()));
            ps.setInt(5, strategie.getProjet().getIdProj());
            if (strategie.getIdUser() == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, strategie.getIdUser());
            }
            ps.setString(7, strategie.getNomStrategie().trim());
            ps.setString(8, strategie.getTypeStrategie().name().toUpperCase(Locale.ROOT));
            ps.setDouble(9, strategie.getBudgetTotal());
            ps.setDouble(10, strategie.getGainEstime());
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



    private List<Strategie> getAllByType(TypeStrategie typeStrategie) {
        String sql = "SELECT * FROM strategies WHERE type = ?";
        List<Strategie> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, typeStrategie.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategies par type: " + e.getMessage(), e);
        }
    }


    @Override
    public List<Strategie> afficher() {
        String sql = "SELECT s.idStrategie, s.versions, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.type, s.budgetTotal, s.gainEstime, " +
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

        String sql = "UPDATE strategies SET versions=?, statusStrategie=?, lockedAt=?, idProj=?, idUser=?, nomStrategie=?, justification=?, type=?, budgetTotal=?, gainEstime=? " +
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

            // 8) type
            ps.setString(8, strategie.getTypeStrategie().name());

            // 9) budgetTotal
            ps.setDouble(9, strategie.getBudgetTotal());

            // 10) gainEstime
            ps.setDouble(10, strategie.getGainEstime());

            // 11) idStrategie (WHERE)
            ps.setInt(11, strategie.getId());

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
        String sql = "SELECT s.idStrategie, s.versions, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.budgetTotal, s.gainEstime, " +
                "s.idProj, s.idUser, s.nomStrategie, s.justification, s.type, " +
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
        s.setNomStrategie(rs.getString("nomStrategie"));
        s.setJustification(rs.getString("justification"));
        s.setTypeStrategie(TypeStrategie.fromDb(rs.getString("type")));
        s.setBudgetTotal(rs.getDouble("budgetTotal"));
        s.setGainEstime(rs.getDouble("gainEstime"));

        int idProj = rs.getInt("idProj");
        if (!rs.wasNull()) {
            Project p = new Project();
            p.setIdProj(idProj);
            getProjectById(idProj).ifPresent(p::setTitleProj);
            s.setProjet(p);
        }
        int idUser = rs.getInt("idUser");
        if (!rs.wasNull()) {
            s.setIdUser(idUser);
        }
        return s;
    }

    private Optional<String> getProjectById(int idProj) {
        String sql = "SELECT titleProj FROM projects WHERE idProj=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture projet par id: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    private void validate(Strategie s, boolean create) {
        if (s == null) throw new IllegalArgumentException("Strategie obligatoire.");
        if (s.getNomStrategie() == null || s.getNomStrategie().isBlank()) throw new IllegalArgumentException("Nom strategie obligatoire.");
        if (s.getProjet() == null || s.getProjet().getIdProj() <= 0) throw new IllegalArgumentException("Projet obligatoire.");
        if (s.getVersion() <= 0) s.setVersion(1);
        if (s.getStatut() == null) s.setStatut(StrategyStatut.EN_COURS);
        if (s.getCreatedAt() == null) s.setCreatedAt(java.time.LocalDateTime.now());
        if (!create && s.getId() <= 0) throw new IllegalArgumentException("idStrategie invalide.");
        if (s.getTypeStrategie() == null) throw new IllegalArgumentException("Type strategie obligatoire, si vous n'etes pas sur veuillez choisir NULL .");
        if (s.getBudgetTotal() < 0) throw new IllegalArgumentException("Budget total >= 0");
        if (s.getBudgetTotal() > 1000000000) throw new IllegalArgumentException("Budget total trop élevé (max 1 milliard) il faut faire une demande de financement pour les projets de cette envergure.");
        if (s.getGainEstime() < 0) throw new IllegalArgumentException("Gain estime >= 0");
        if (s.getGainEstime() > 1000000000) throw new IllegalArgumentException("Gain estime trop élevé veuillez revoir votre estimation.");
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
            if (status == StrategyStatut.ACCEPTEE && normalized.contains("ACCEPTEE")) {
                return literal;
            }
            if (status == StrategyStatut.REFUSEE && normalized.contains("REFUSEE")) {
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
    SELECT s.idStrategie, s.versions, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.budgetTotal, s.gainEstime,
           s.idProj, s.idUser, s.nomStrategie, s.justification, s.type,
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
                ? "UPDATE strategies SET statusStrategie=?, " +
                "lockedAt = CASE WHEN ? THEN NOW() ELSE lockedAt END, " +
                "idProj = CASE WHEN ? THEN idProj ELSE NULL END, " +
                "justification = ? WHERE idStrategie=?"
                : "UPDATE strategies SET statusStrategie=?, " +
                "lockedAt = CASE WHEN ? THEN NOW() ELSE lockedAt END, " +
                "justification=? WHERE idStrategie=?";


        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            String status = accepted ? StrategyStatut.ACCEPTEE.toDb()
                    : StrategyStatut.REFUSEE.toDb();

            String justification = accepted ? null : justificationIfRefused.trim();

            if (detachOnRefuse) {

                ps.setString(1, status);
                ps.setBoolean(2, accepted);     // lockedAt
                ps.setBoolean(3, accepted);     // idProj logic
                ps.setString(4, justification);
                ps.setInt(5, idStrategie);


            } else {

                ps.setString(1, status);
                ps.setBoolean(2, accepted);     // lockedAt
                ps.setString(3, justification);
                ps.setInt(4, idStrategie);
            }


            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur mise à jour décision strategie: " + e.getMessage(), e);
        }
        NotificationManager.getInstance().addNotification(new Notification(
                "Décision strategie",
                "La strategie a été " + (accepted ? "acceptée" : "refusée")
        ));
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

    public double CalculROI(double gainEstime, double budgetTotal) {
        if (budgetTotal == 0) {
            return gainEstime > 0 ? Double.POSITIVE_INFINITY : 0;
        }
        return (gainEstime - budgetTotal) / budgetTotal;
    }


    public Strategie getStrategieByNom(String nomStrategie) {
        String sql = "SELECT * FROM strategies WHERE nomStrategie=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, nomStrategie);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategie par nom: " + e.getMessage(), e);
        }
    }
    private String norm(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private java.util.Set<String> tokens(String s) {
        String n = norm(s);
        if (n.isBlank()) return java.util.Set.of();
        return new java.util.HashSet<>(java.util.Arrays.asList(n.split(" ")));
    }

    private Set<String> charNgrams(String s, int n) {
        s = norm(s).replace(" ", "");
        if (s.isBlank()) return Set.of();
        if (s.length() <= n) return Set.of(s);
        Set<String> grams = new HashSet<>();
        for (int i = 0; i <= s.length() - n; i++) {
            grams.add(s.substring(i, i + n));
        }
        return grams;
    }

    private double jaccardSet(Set<String> A, Set<String> B) {
        if (A.isEmpty() && B.isEmpty()) return 1.0;
        if (A.isEmpty() || B.isEmpty()) return 0.0;

        Set<String> inter = new HashSet<>(A);
        inter.retainAll(B);

        Set<String> union = new HashSet<>(A);
        union.addAll(B);

        return (double) inter.size() / (double) union.size();
    }

    private double jaccardName(String a, String b) {
        return jaccardSet(charNgrams(a, 3), charNgrams(b, 3));
    }

    private double closeness(Double x, Double y, double tolerancePct) {
        if (x == null || y == null) return 0.0;
        double denom = Math.max(Math.abs(x), Math.abs(y));
        if (denom == 0) return 1.0;
        double diffPct = Math.abs(x - y) / denom;

        if (diffPct <= tolerancePct) return 1.0;
        return Math.max(0.0, 1.0 - diffPct);
    }

    private double jaccardObjectives(java.util.Set<Integer> a, java.util.Set<Integer> b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        var inter = new java.util.HashSet<>(a);
        inter.retainAll(b);

        var union = new java.util.HashSet<>(a);
        union.addAll(b);

        return (double) inter.size() / (double) union.size();
    }

    private boolean sameType(Strategie a, Strategie b) {
        if (a.getTypeStrategie() == null || b.getTypeStrategie() == null) return false;
        return a.getTypeStrategie().name().equals(b.getTypeStrategie().name());
    }
    public double similarityScore(Strategie a, Strategie b,
                                  java.util.Set<Integer> aObjIds,
                                  java.util.Set<Integer> bObjIds) {

        double nameSim = jaccardName(a.getNomStrategie(), b.getNomStrategie());
        double budgetSim = closeness(a.getBudgetTotal(), b.getBudgetTotal(), 0.10);
        double gainSim   = closeness(a.getGainEstime(), b.getGainEstime(), 0.10);

        boolean hasObj = (aObjIds != null && bObjIds != null);
        double objSim = hasObj ? jaccardObjectives(aObjIds, bObjIds) : 0.0;

        if (hasObj) {
            return 0.20 * nameSim + 0.35 * budgetSim + 0.35 * gainSim + 0.10 * objSim;
        } else {
            // redistribute objective weight when objectives not known yet
// no objectives yet
            return 0.35 * nameSim + 0.325 * budgetSim + 0.325 * gainSim;        }
    }

      SimilarityResult checkUniquenessGlobal(Strategie incoming,
                                             java.util.Set<Integer> incomingObjIds,
                                             java.util.List<Strategie> candidates,
                                             java.util.function.Function<Integer, java.util.Set<Integer>> loadObjIds) {

        double bestScore = -1.0;
        Strategie best = null;

        for (Strategie ex : candidates) {
            if (ex.getId() == incoming.getId()) continue;

            // Recommended: duplicates must be same type
            if (!sameType(incoming, ex)) continue;

            java.util.Set<Integer> exObjIds = null;
            if (incomingObjIds != null && loadObjIds != null) {
                exObjIds = loadObjIds.apply(ex.getId());
            }

            double score = similarityScore(incoming, ex, incomingObjIds, exObjIds);

            if (score > bestScore) {
                bestScore = score;
                best = ex;
            }
            boolean sameProject =
                    incoming.getProjet() != null && ex.getProjet() != null &&
                            incoming.getProjet().getIdProj() == ex.getProjet().getIdProj();

            if (sameProject) {
                double budgetHard = closeness(incoming.getBudgetTotal(), ex.getBudgetTotal(), 0.05); // 5%
                double gainHard   = closeness(incoming.getGainEstime(), ex.getGainEstime(), 0.10);  // 10%

                if (budgetHard >= 1.0 && gainHard >= 0.90) {
                    // treat as duplicate even if name differs
                    return new SimilarityResult(true, Math.max(score, 0.95), ex.getId(), ex.getNomStrategie());
                }
            }

            // HARD RULE: same numbers (very close) => duplicate even if name differs
            double budgetHard = closeness(incoming.getBudgetTotal(), ex.getBudgetTotal(), 0.03);
            double gainHard   = closeness(incoming.getGainEstime(), ex.getGainEstime(), 0.03);
            if (budgetHard >= 1.0 && gainHard >= 1.0) {
                return new SimilarityResult(true, Math.max(score, 1.0), ex.getId(), ex.getNomStrategie());
            }

        }

        if (best == null) {
            return new SimilarityResult(false, 0.0, -1, null);
        }

        boolean duplicate = bestScore >= 0.75;
        return new SimilarityResult(duplicate, bestScore, best.getId(), best.getNomStrategie());
    }






}
