package com.advisora.Services.strategie;

import com.advisora.Model.projet.Project;
import com.advisora.Model.strategie.Notification;
import com.advisora.Model.strategie.SimilarityResult;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.IService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.TypeStrategie;
import com.advisora.enums.UserRole;
import com.advisora.utils.MyConnection;
import com.advisora.utils.news.OllamaClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

public class ServiceStrategie implements IService<Strategie> {

    // =========================
    // CREATE
    // =========================
    @Override
    public void ajouter(Strategie strategie) {
        validate(strategie, true);

        String sql = """
            INSERT INTO strategies
            (statusStrategie, CreatedAtS, lockedAt, idProj, idUser, nomStrategie, type, budgetTotal, gainEstime, DureeTerme)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // 1) status
            if (!Boolean.TRUE.equals(strategie.getApprobation())) {
                ps.setString(1, "En_attente");
            } else if (strategie.getProjet() == null) {
                ps.setString(1, "Non_affectée");
            } else {
                ps.setString(1, "En_cours");
            }

            // 2) dates
            if (strategie.getCreatedAt() == null) strategie.setCreatedAt(LocalDateTime.now());
            ps.setTimestamp(2, Timestamp.valueOf(strategie.getCreatedAt()));
            ps.setTimestamp(3, strategie.getLockedAt() == null ? null : Timestamp.valueOf(strategie.getLockedAt()));

            // 3) project nullable
            if (strategie.getProjet() == null) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, strategie.getProjet().getIdProj());

            // 4) user nullable
            if (strategie.getIdUser() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, strategie.getIdUser());

            // 5) fields
            ps.setString(6, safe(strategie.getNomStrategie()));
            ps.setString(7, strategie.getTypeStrategie().name());
            ps.setDouble(8, strategie.getBudgetTotal());
            ps.setDouble(9, strategie.getGainEstime());

            // ✅ DureeTerme is INT in DB + model
            ps.setInt(10, strategie.getDureeTerme());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) strategie.setId(rs.getInt(1));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout strategie: " + e.getMessage(), e);
        }
    }

    // =========================
    // READ ALL
    // =========================
    @Override
    public List<Strategie> afficher() {
        String sql = """
            SELECT s.idStrategie, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.type,
                   s.budgetTotal, s.gainEstime, s.idProj, s.idUser, s.nomStrategie,
                   s.justification, s.DureeTerme,
                   p.titleProj
            FROM strategies s
            LEFT JOIN projects p ON p.idProj = s.idProj
            ORDER BY s.idStrategie DESC
        """;

        List<Strategie> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(map(rs));
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategies: " + e.getMessage(), e);
        }
    }

    // =========================
    // READ BY ID
    // =========================
    public Strategie getById(int idStrategie) {
        String sql = """
            SELECT s.idStrategie, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.type,
                   s.budgetTotal, s.gainEstime, s.idProj, s.idUser, s.nomStrategie,
                   s.justification, s.DureeTerme,
                   p.titleProj
            FROM strategies s
            LEFT JOIN projects p ON p.idProj = s.idProj
            WHERE s.idStrategie=?
        """;

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.setInt(1, idStrategie);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return map(rs);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategie: " + e.getMessage(), e);
        }
    }

    // =========================
    // UPDATE
    // =========================
    @Override
    public void modifier(Strategie strategie) {
        validate(strategie, false);

        Integer newProjId = (strategie.getProjet() != null && strategie.getProjet().getIdProj() > 0)
                ? strategie.getProjet().getIdProj()
                : null;

        Integer oldProjId = getCurrentProjectId(strategie.getId());

        StrategyStatut statusToSave = strategie.getStatut();
        String justification = strategie.getJustification();

        // if project changed -> move to EN_COURS + clear justification
        if (!Objects.equals(oldProjId, newProjId)) {
            statusToSave = StrategyStatut.EN_COURS;
            justification = null;
            strategie.setStatut(statusToSave);
        }

        String sql = """
            UPDATE strategies
            SET statusStrategie=?, lockedAt=?, idProj=?, idUser=?, nomStrategie=?, justification=?,
                type=?, budgetTotal=?, gainEstime=?, DureeTerme=?
            WHERE idStrategie=?
        """;

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            // 1 status
            ps.setString(1, resolveDbStrategyStatus(cnx, statusToSave));

            // 2 lockedAt
            ps.setTimestamp(2, strategie.getLockedAt() == null ? null : Timestamp.valueOf(strategie.getLockedAt()));

            // 3 idProj
            if (newProjId == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, newProjId);

            // 4 idUser ✅ FIXED (was wrong in your old code)
            if (strategie.getIdUser() == null) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, strategie.getIdUser());

            // 5 nomStrategie
            ps.setString(5, safe(strategie.getNomStrategie()));

            // 6 justification
            ps.setString(6, (justification == null || justification.isBlank()) ? null : justification.trim());

            // 7 type
            ps.setString(7, strategie.getTypeStrategie().name());

            // 8 budgetTotal
            ps.setDouble(8, strategie.getBudgetTotal());

            // 9 gainEstime
            ps.setDouble(9, strategie.getGainEstime());

            // 10 DureeTerme int
            ps.setInt(10, strategie.getDureeTerme());

            // 11 where
            ps.setInt(11, strategie.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification strategie: " + e.getMessage(), e);
        }
    }

    // =========================
    // DELETE
    // =========================
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

    // =========================
    // PROJECT CHANGE RULES
    // =========================
    public void updateProject(Strategie s, UserRole role) {
        if (s.getStatut() != StrategyStatut.ACCEPTEE || s.getProjet() == null) {
            modifier(s);
            return;
        }

        if (role == UserRole.ADMIN) {
            modifier(s);
            return;
        }

        LocalDateTime ref = s.getLockedAt();
        if (ref == null) {
            throw new IllegalStateException("Changement projet bloqué: lockedAt inconnue.");
        }

        long minutes = java.time.Duration.between(ref, LocalDateTime.now()).toMinutes();
        if (minutes > 120) {
            throw new IllegalArgumentException("Vous ne pouvez plus changer le projet après 2 heures. Seul l'admin peut.");
        }

        modifier(s);
    }

    // =========================
    // DECISION
    // =========================
    public void applyDecision(int idStrategie, boolean accepted, String justificationIfRefused, boolean detachOnRefuse) {

        if (!accepted) {
            if (justificationIfRefused == null || justificationIfRefused.trim().isEmpty()) {
                throw new IllegalArgumentException("Le refus nécessite une justification.");
            }
        }

        String sql = detachOnRefuse
                ? """
                    UPDATE strategies
                    SET statusStrategie=?,
                        lockedAt = CASE WHEN ? THEN NOW() ELSE lockedAt END,
                        idProj   = CASE WHEN ? THEN idProj ELSE NULL END,
                        justification = ?
                    WHERE idStrategie=?
                  """
                : """
                    UPDATE strategies
                    SET statusStrategie=?,
                        lockedAt = CASE WHEN ? THEN NOW() ELSE lockedAt END,
                        justification=?
                    WHERE idStrategie=?
                  """;

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            String status = accepted ? StrategyStatut.ACCEPTEE.toDb()
                    : StrategyStatut.REFUSEE.toDb();
            String justification = accepted ? null : justificationIfRefused.trim();

            if (detachOnRefuse) {
                ps.setString(1, status);
                ps.setBoolean(2, accepted);
                ps.setBoolean(3, accepted);
                ps.setString(4, justification);
                ps.setInt(5, idStrategie);
            } else {
                ps.setString(1, status);
                ps.setBoolean(2, accepted);
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

    // =========================
    // UPDATE STATUS (simple)
    // =========================
    public void updateStatut(int idStrategie, StrategyStatut statut) {
        if (idStrategie <= 0) throw new IllegalArgumentException("ID invalide");
        if (statut == null) throw new IllegalArgumentException("Statut obligatoire");

        String sql = "UPDATE strategies SET statusStrategie=? WHERE idStrategie=?";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            // Use resolver to match enum literals in DB safely
            ps.setString(1, resolveDbStrategyStatus(cnx, statut));
            ps.setInt(2, idStrategie);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur update statut: " + e.getMessage(), e);
        }
    }

    // =========================
    // READ BY PROJECT
    // =========================
    public List<Strategie> getByProject(int projectId) {
        String sql = """
            SELECT s.idStrategie, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.type,
                   s.budgetTotal, s.gainEstime, s.idProj, s.idUser, s.nomStrategie,
                   s.justification, s.DureeTerme,
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
                while (rs.next()) list.add(map(rs));
            }

            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategies par projet: " + e.getMessage(), e);
        }
    }

    // =========================
    // LIST PROJECTS
    // =========================
    public List<Project> listProjets() {
        String sql = "SELECT idProj, titleProj FROM projects WHERE stateProj='ACCEPTED' ORDER BY idProj DESC";
        List<Project> list = new ArrayList<>();

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Project p = new Project();
                p.setIdProj(rs.getInt("idProj"));
                p.setTitleProj(rs.getString("titleProj"));
                list.add(p);
            }
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture projets: " + e.getMessage(), e);
        }
    }

    // =========================
    // GET BY NAME
    // =========================
    public Strategie getStrategieByNom(String nomStrategie) {
        if (nomStrategie == null || nomStrategie.isBlank()) return null;

        String sql = """
            SELECT s.idStrategie, s.statusStrategie, s.CreatedAtS, s.lockedAt, s.type,
                   s.budgetTotal, s.gainEstime, s.idProj, s.idUser, s.nomStrategie,
                   s.justification, s.DureeTerme,
                   p.titleProj
            FROM strategies s
            LEFT JOIN projects p ON p.idProj = s.idProj
            WHERE s.nomStrategie = ?
            LIMIT 1
        """;

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.setString(1, nomStrategie.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture strategie par nom: " + e.getMessage(), e);
        }
    }

    // =========================
    // RECOMMENDATION (keeps your logic)
    // =========================

    /**
     * PREVIEW ONLY:
     * - if duplicate exists => return existing one (NO DB changes)
     * - else => generate draft (NOT saved)
     */
    public Strategie recommendDraftStrategy(Project project) {
        if (project == null || project.getIdProj() <= 0) {
            throw new IllegalArgumentException("Projet invalide.");
        }

        List<Strategie> candidates = getByProject(project.getIdProj());

        Strategie incoming = new Strategie();
        incoming.setId(0);
        incoming.setProjet(project);
        incoming.setNomStrategie("");
        incoming.setTypeStrategie(TypeStrategie.AUTRE);

        double budget = (project.getBudgetProj() == 0) ? 0.0 : project.getBudgetProj();
        incoming.setBudgetTotal(budget);
        incoming.setGainEstime(budget * 0.20);

        // int days
        incoming.setDureeTerme(180);

        SimilarityResult sim = checkUniquenessGlobal(incoming, null, candidates, null);

        if (sim.isDuplicate()) {
            int matchId = sim.getBestMatchingStrategyId(); // ✅ based on your SimilarityResult class
            return candidates.stream()
                    .filter(s -> s.getId() == matchId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Matching strategy not found: id=" + matchId));
        }

        Strategie draft = generateDraftFromGemini(project);
        draft.setProjet(project);
        draft.setIdUser(SessionContext.getCurrentUserId());
        draft.setStatut(StrategyStatut.EN_COURS);
        draft.setCreatedAt(LocalDateTime.now());
        return draft;
    }

    /**
     * This one can update DB if you want (your previous logic did).
     * I keep it but ensure DureeTerme uses int.
     */
    public Strategie recommendStrategy(Project project) {
        if (project == null || project.getIdProj() <= 0) {
            throw new IllegalArgumentException("Projet invalide.");
        }

        List<Strategie> candidates = getByProject(project.getIdProj());

        Strategie incoming = new Strategie();
        incoming.setProjet(project);
        incoming.setNomStrategie("");
        incoming.setBudgetTotal(project.getBudgetProj() == 0 ? 0.0 : project.getBudgetProj());
        incoming.setGainEstime((project.getBudgetProj() == 0 ? 0.0 : project.getBudgetProj()) * 0.20);
        incoming.setDureeTerme(180);
        incoming.setTypeStrategie(TypeStrategie.AUTRE);

        SimilarityResult result = checkUniquenessGlobal(incoming, null, candidates, null);

        if (result.isDuplicate()) {
            int matchId = result.getBestMatchingStrategyId(); // ✅ match SimilarityResult
            Strategie best = candidates.stream()
                    .filter(s -> s.getId() == matchId)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Matching strategy not found"));

            StrategyStatut target = result.getBestScore() >= 0.90
                    ? StrategyStatut.ACCEPTEE
                    : StrategyStatut.EN_COURS;

            if (best.getStatut() != target) {
                updateStatut(best.getId(), target);
                best.setStatut(target);
            }
            return best;
        }

        return generateDraftFromGemini(project);
    }

    /**
     * Persists a draft (CONFIRM action).
     */
    public Strategie confirmRecommendedStrategy(Strategie draft) {
        if (draft == null) throw new IllegalArgumentException("Stratégie invalide.");

        if (draft.getProjet() == null || draft.getProjet().getIdProj() <= 0) {
            throw new IllegalArgumentException("Projet obligatoire pour enregistrer.");
        }
        if (draft.getIdUser() == null) draft.setIdUser(SessionContext.getCurrentUserId());
        if (draft.getStatut() == null) draft.setStatut(StrategyStatut.EN_COURS);
        if (draft.getCreatedAt() == null) draft.setCreatedAt(LocalDateTime.now());

        if (draft.getDureeTerme() <= 0) {
            throw new IllegalArgumentException("Durée du terme doit être > 0 (en jours).");
        }

        ajouter(draft);
        return draft;
    }

    public int getBestMatchingStrategyId(Project project) {
        if (project == null || project.getIdProj() <= 0) {
            throw new IllegalArgumentException("project cannot be null/invalid");
        }

        List<Strategie> candidates = getByProject(project.getIdProj());
        if (candidates.isEmpty()) return -1;

        Strategie incoming = new Strategie();
        incoming.setProjet(project);
        incoming.setNomStrategie("");
        incoming.setBudgetTotal(project.getBudgetProj() == 0 ? 0.0 : project.getBudgetProj());
        incoming.setGainEstime((project.getBudgetProj() == 0 ? 0.0 : project.getBudgetProj()) * 0.20);
        incoming.setDureeTerme(180);
        incoming.setTypeStrategie(TypeStrategie.AUTRE);

        SimilarityResult result = checkUniquenessGlobal(incoming, null, candidates, null);
        if (!result.isDuplicate()) return -1;

        return result.getBestMatchingStrategyId(); // ✅
    }

    // =========================
    // GEMINI DRAFT
    // =========================
    private Strategie generateDraftFromGemini(Project project) {
        OllamaClient gemini = new OllamaClient(
                System.getenv("GEMINI_API_KEY"),
                "gemini-1.5-flash"
        );

        double budget = (project.getBudgetProj() == 0) ? 0.0 : project.getBudgetProj();

        String prompt = """
You are a consultant. Generate ONE strategy proposal for this project.

Project title: %s
Budget: %.2f
Type: %s
Progress: %.0f%%

Return ONLY JSON with EXACT keys:
{
  "nomStrategie": "string",
  "justification": "string",
  "budgetTotal": number,
  "gainEstime": number,
  "DureeTerme": 180,
  "typeStrategie": "AUTRE"
}
""".formatted(
                safe(project.getTitleProj()),
                budget,
                String.valueOf(project.getTypeProj()),
                project.getAvancementProj()
        );

        Optional<JsonNode> jsonOpt;
        try {
            jsonOpt = gemini.generateJson(prompt);
        } catch (Exception e) {
            throw new RuntimeException("Gemini unreachable: " + e.getMessage(), e);
        }

        if (jsonOpt.isEmpty()) {
            throw new RuntimeException("Gemini returned empty/malformed JSON");
        }

        JsonNode reply = jsonOpt.get();

        Strategie s = new Strategie();
        s.setNomStrategie(reply.path("nomStrategie").asText("").trim());
        s.setJustification(reply.path("justification").asText("").trim());
        s.setBudgetTotal(reply.path("budgetTotal").asDouble(budget));
        s.setGainEstime(reply.path("gainEstime").asDouble(budget * 0.2));

        // ✅ int
        s.setDureeTerme(reply.path("DureeTerme").asInt(180));

        s.setTypeStrategie(TypeStrategie.AUTRE);
        return s;
    }

    // =========================
    // OBJECTIVES
    // =========================
    public Set<Integer> getObjectives(Integer id) {
        String sql = "SELECT idObjective FROM strategy_objectives WHERE idStrategie=?";
        Set<Integer> result = new HashSet<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getInt(1));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture objectives strategie: " + e.getMessage(), e);
        }
    }

    public Set<Integer> getObjectivesByProject(int idProj) {
        String sql = """
            SELECT so.idObjective
            FROM strategy_objectives so
            JOIN strategies s ON s.idStrategie = so.idStrategie
            WHERE s.idProj = ?
        """;
        Set<Integer> result = new HashSet<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getInt(1));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture objectives par projet: " + e.getMessage(), e);
        }
    }

    // =========================
    // SIMILARITY CORE
    // =========================
    public SimilarityResult checkUniquenessGlobal(Strategie incoming,
                                                  Set<Integer> incomingObjIds,
                                                  List<Strategie> candidates,
                                                  Function<Integer, Set<Integer>> loadObjIds) {

        double bestScore = -1.0;
        Strategie best = null;

        for (Strategie ex : candidates) {
            if (ex.getId() == incoming.getId()) continue;
            if (!sameType(incoming, ex)) continue;

            Set<Integer> exObjIds = null;
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
                double budgetHard = closeness(incoming.getBudgetTotal(), ex.getBudgetTotal(), 0.05);
                double gainHard = closeness(incoming.getGainEstime(), ex.getGainEstime(), 0.10);

                if (budgetHard >= 1.0 && gainHard >= 0.90) {
                    return new SimilarityResult(true, Math.max(score, 0.95), ex.getId(), ex.getNomStrategie());
                }
            }

            double budgetHard = closeness(incoming.getBudgetTotal(), ex.getBudgetTotal(), 0.03);
            double gainHard = closeness(incoming.getGainEstime(), ex.getGainEstime(), 0.03);
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

    public double similarityScore(Strategie a, Strategie b,
                                  Set<Integer> aObjIds,
                                  Set<Integer> bObjIds) {

        double nameSim = jaccardName(a.getNomStrategie(), b.getNomStrategie());
        double budgetSim = closeness(a.getBudgetTotal(), b.getBudgetTotal(), 0.10);
        double gainSim = closeness(a.getGainEstime(), b.getGainEstime(), 0.10);

        boolean hasObj = (aObjIds != null && bObjIds != null);
        double objSim = hasObj ? jaccardObjectives(aObjIds, bObjIds) : 0.0;

        if (hasObj) {
            return 0.20 * nameSim + 0.35 * budgetSim + 0.35 * gainSim + 0.10 * objSim;
        } else {
            return 0.35 * nameSim + 0.325 * budgetSim + 0.325 * gainSim;
        }
    }

    // =========================
    // MAPPING (IMPORTANT)
    // =========================
    private Strategie map(ResultSet rs) throws SQLException {
        Strategie s = new Strategie();

        s.setId(rs.getInt("idStrategie"));
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

        // ✅ FIX: real setter (INT)
        s.setDureeTerme(rs.getInt("DureeTerme"));

        int idProj = rs.getInt("idProj");
        if (!rs.wasNull()) {
            Project p = new Project();
            p.setIdProj(idProj);

            String title = null;
            try { title = rs.getString("titleProj"); } catch (SQLException ignored) {}
            if (title != null) p.setTitleProj(title);

            s.setProjet(p);
        }

        int idUser = rs.getInt("idUser");
        if (!rs.wasNull()) s.setIdUser(idUser);

        return s;
    }

    // =========================
    // VALIDATION (INT)
    // =========================
    private void validate(Strategie s, boolean create) {
        if (s == null) throw new IllegalArgumentException("Strategie obligatoire.");
        if (s.getNomStrategie() == null || s.getNomStrategie().isBlank())
            throw new IllegalArgumentException("Nom strategie obligatoire.");

        if (s.getStatut() == null) throw new IllegalArgumentException("Statut strategie obligatoire.");
        if (s.getCreatedAt() == null) s.setCreatedAt(LocalDateTime.now());
        if (!create && s.getId() <= 0) throw new IllegalArgumentException("idStrategie invalide.");

        if (s.getTypeStrategie() == null || s.getTypeStrategie() == TypeStrategie.NULL)
            throw new IllegalArgumentException("Type strategie obligatoire.");

        if (s.getBudgetTotal() < 0) throw new IllegalArgumentException("Budget total >= 0");
        if (s.getBudgetTotal() > 1_000_000_000)
            throw new IllegalArgumentException("Budget total trop élevé (max 1 milliard).");

        if (s.getGainEstime() < 0) throw new IllegalArgumentException("Gain estimé >= 0");
        if (s.getGainEstime() > 1_000_000_000)
            throw new IllegalArgumentException("Gain estimé trop élevé.");

        // ✅ int days
        if (s.getDureeTerme() <= 0) {
            throw new IllegalArgumentException("Durée du terme doit être > 0 (en jours).");
        }
    }

    // =========================
    // SMALL UTILS
    // =========================
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
        if (budgetTotal == 0) return gainEstime > 0 ? Double.POSITIVE_INFINITY : 0;
        return (gainEstime - budgetTotal) / budgetTotal;
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // =========================
    // ENUM LITERAL RESOLUTION (keep)
    // =========================
    private String resolveDbStrategyStatus(Connection cnx, StrategyStatut status) {
        StrategyStatut safeStatus = status == null ? StrategyStatut.EN_COURS : status;
        String sql = "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='strategies' AND COLUMN_NAME='statusStrategie'";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String columnType = rs.getString(1);
                String mapped = mapEnumLiteral(columnType, safeStatus);
                if (mapped != null) return mapped;
            }
        } catch (SQLException ignored) {}
        return safeStatus.toDb();
    }

    private String mapEnumLiteral(String columnType, StrategyStatut status) {
        if (columnType == null || !columnType.toLowerCase(Locale.ROOT).startsWith("enum(")) return null;
        String raw = columnType.substring(5, columnType.length() - 1);
        String[] values = raw.split(",");
        for (String v : values) {
            String literal = v.trim();
            if (literal.startsWith("'") && literal.endsWith("'") && literal.length() >= 2) {
                literal = literal.substring(1, literal.length() - 1);
            }
            String normalized = normalizeLiteral(literal);
            if (status == StrategyStatut.EN_COURS && normalized.contains("EN_COURS")) return literal;
            if (status == StrategyStatut.ACCEPTEE && normalized.contains("ACCEPTEE")) return literal;
            if (status == StrategyStatut.REFUSEE && normalized.contains("REFUSEE")) return literal;
            if (status == StrategyStatut.EN_ATTENTE && normalized.contains("EN_ATTENTE")) return literal;
            if (status == StrategyStatut.Non_affectée && normalized.contains("NON_AFFECTE")) return literal;
        }
        return null;
    }

    private String normalizeLiteral(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('-', '_')
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    // =========================
    // SIMILARITY HELPERS
    // =========================
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

    private double jaccardObjectives(Set<Integer> a, Set<Integer> b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        var inter = new HashSet<>(a);
        inter.retainAll(b);

        var union = new HashSet<>(a);
        union.addAll(b);

        return (double) inter.size() / (double) union.size();
    }

    private boolean sameType(Strategie a, Strategie b) {
        if (a.getTypeStrategie() == null || b.getTypeStrategie() == null) return true;
        if (a.getTypeStrategie() == TypeStrategie.NULL || b.getTypeStrategie() == TypeStrategie.NULL) return true;
        return a.getTypeStrategie() == b.getTypeStrategie();
    }
}