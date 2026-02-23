package com.advisora.Services.projet;

import com.advisora.Model.projet.Project;
import com.advisora.Model.projet.ProjectBadgeScore;
import com.advisora.Model.projet.Task;
import com.advisora.enums.DecisionStatus;
import com.advisora.enums.ProjectStatus;
import com.advisora.enums.TaskStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectBadgeService {
    private static final double W_TEMPORAL = 0.45;
    private static final double W_RELIABILITY = 0.25;
    private static final double W_REGULARITY = 0.15;
    private static final double W_STABILITY = 0.15;

    private final Clock clock;
    private Boolean taskTableAvailableCache = null;

    public ProjectBadgeService() {
        this(Clock.systemDefaultZone());
    }

    public ProjectBadgeService(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public ProjectBadgeScore compute(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project requis");
        }
        Map<Integer, ProjectBadgeScore> out = computeForProjects(Collections.singletonList(project));
        return out.get(project.getIdProj());
    }

    public Map<Integer, ProjectBadgeScore> computeForProjects(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Project> validProjects = projects.stream()
                .filter(p -> p != null && p.getIdProj() > 0)
                .toList();
        if (validProjects.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Integer> projectIds = validProjects.stream().map(Project::getIdProj).collect(Collectors.toSet());
        Map<Integer, List<Task>> tasksByProject = loadTasksByProject(projectIds);
        DecisionData decisionData = loadDecisionData(projectIds);

        Map<Integer, ProjectBadgeScore> out = new LinkedHashMap<>();
        for (Project project : validProjects) {
            List<Task> tasks = tasksByProject.getOrDefault(project.getIdProj(), List.of());
            List<LocalDateTime> decisionDates = decisionData.datesByProject.getOrDefault(project.getIdProj(), List.of());
            boolean hadRefusal = decisionData.refusedProjects.contains(project.getIdProj())
                    || project.getStateProj() == ProjectStatus.REFUSED;
            out.put(project.getIdProj(), compute(project, tasks, decisionDates, hadRefusal));
        }
        return out;
    }

    public ProjectBadgeScore compute(Project project,
                                     List<Task> tasks,
                                     List<LocalDateTime> decisionDates,
                                     boolean hadRefusalDecisionHistory) {
        if (project == null) {
            throw new IllegalArgumentException("project requis");
        }

        List<Task> safeTasks = tasks == null ? List.of() : tasks;
        List<LocalDateTime> safeDecisionDates = decisionDates == null ? List.of() : decisionDates;

        boolean hadRefusalHistory = hadRefusalDecisionHistory || project.getStateProj() == ProjectStatus.REFUSED;
        double temporal = computeTemporalScore(project);
        double reliability = hadRefusalHistory ? 0.0 : 100.0;
        double regularity = computeRegularityScore(project, safeTasks, safeDecisionDates);
        double stability = computeStabilityScore(project, safeTasks, safeDecisionDates);

        double pbs = clamp100(
                (W_TEMPORAL * temporal) +
                (W_RELIABILITY * reliability) +
                (W_REGULARITY * regularity) +
                (W_STABILITY * stability)
        );

        String badge = badgeFor(pbs, hadRefusalHistory);

        ProjectBadgeScore score = new ProjectBadgeScore();
        score.setProjectId(project.getIdProj());
        score.setTemporalScore(temporal);
        score.setReliabilityScore(reliability);
        score.setRegularityScore(regularity);
        score.setStabilityScore(stability);
        score.setPbs(pbs);
        score.setBadge(badge);
        score.setHadRefusalHistory(hadRefusalHistory);
        return score;
    }

    private double computeTemporalScore(Project project) {
        double progressPercent = clamp100(project.getAvancementProj());
        LocalDate now = nowDate();
        LocalDate createdDate = toLocalDate(project.getCreatedAtProj(), now);
        long daysElapsed = Math.max(1, ChronoUnit.DAYS.between(createdDate, now) + 1);
        double elapsedPercent = Math.min(100.0, daysElapsed);
        double efficiencyRatio = progressPercent / Math.max(1.0, elapsedPercent);
        return clamp100(efficiencyRatio * 100.0);
    }

    private double computeRegularityScore(Project project, List<Task> tasks, List<LocalDateTime> decisions) {
        LocalDate now = nowDate();
        LocalDate created = toLocalDate(project.getCreatedAtProj(), now);
        int totalWeeks = totalWeeks(created, now);
        if (totalWeeks <= 0) {
            return 0.0;
        }

        Set<Integer> activeWeeks = new HashSet<>();
        for (Task task : tasks) {
            LocalDate date = toLocalDate(task.getCreatedAt(), null);
            if (date != null && !date.isBefore(created) && !date.isAfter(now)) {
                activeWeeks.add(weekKey(date));
            }
        }
        for (LocalDateTime decisionDate : decisions) {
            if (decisionDate == null) continue;
            LocalDate date = decisionDate.toLocalDate();
            if (!date.isBefore(created) && !date.isAfter(now)) {
                activeWeeks.add(weekKey(date));
            }
        }

        return clamp100((activeWeeks.size() * 100.0) / totalWeeks);
    }

    private double computeStabilityScore(Project project, List<Task> tasks, List<LocalDateTime> decisions) {
        if (tasks.isEmpty() && decisions.isEmpty()) {
            return 50.0;
        }

        LocalDate now = nowDate();
        LocalDate created = toLocalDate(project.getCreatedAtProj(), now);
        LocalDate startWeek = weekStart(created);
        LocalDate endWeek = weekStart(now);

        List<Task> orderedTasks = new ArrayList<>(tasks);
        orderedTasks.sort((a, b) -> {
            LocalDate da = toLocalDate(a.getCreatedAt(), created);
            LocalDate db = toLocalDate(b.getCreatedAt(), created);
            int cmp = da.compareTo(db);
            if (cmp != 0) return cmp;
            return Integer.compare(a.getId(), b.getId());
        });

        int idx = 0;
        double doneWeight = 0.0;
        double totalWeight = 0.0;
        int lateTransitions = 0;
        int lateWeeks = 0;
        int totalWeeks = 0;
        boolean wasLate = false;

        for (LocalDate cursor = startWeek; !cursor.isAfter(endWeek); cursor = cursor.plusWeeks(1)) {
            totalWeeks++;
            LocalDate weekEnd = cursor.plusDays(6);
            LocalDate evalDate = weekEnd.isAfter(now) ? now : weekEnd;

            while (idx < orderedTasks.size()) {
                Task t = orderedTasks.get(idx);
                LocalDate taskDate = toLocalDate(t.getCreatedAt(), created);
                if (taskDate.isAfter(evalDate)) {
                    break;
                }
                totalWeight += Math.max(0, t.getWeight());
                if (safeStatus(t) == TaskStatus.DONE) {
                    doneWeight += Math.max(0, t.getWeight());
                }
                idx++;
            }

            double progressProxy = totalWeight <= 0.0 ? 0.0 : (doneWeight / totalWeight) * 100.0;
            long daysSinceCreation = Math.max(1, ChronoUnit.DAYS.between(created, evalDate) + 1);
            double elapsedPercent = Math.min(100.0, daysSinceCreation);
            boolean late = progressProxy < elapsedPercent;

            if (late) {
                lateWeeks++;
                if (!wasLate) {
                    lateTransitions++;
                }
            }
            wasLate = late;
        }

        if (totalWeeks <= 0) {
            return 50.0;
        }

        double lateDurationRatio = lateWeeks / (double) totalWeeks;
        double stabilityPenalty = Math.min(100.0, lateTransitions * 20.0 + lateDurationRatio * 80.0);
        return clamp100(100.0 - stabilityPenalty);
    }

    private String badgeFor(double pbs, boolean hadRefusalHistory) {
        String badge;
        if (pbs < 40.0) {
            badge = "BRONZE";
        } else if (pbs < 65.0) {
            badge = "ARGENT";
        } else if (pbs < 85.0) {
            badge = "OR";
        } else {
            badge = "PLATINE";
        }
        if (hadRefusalHistory && "PLATINE".equals(badge)) {
            return "OR";
        }
        return badge;
    }

    private Map<Integer, List<Task>> loadTasksByProject(Set<Integer> projectIds) {
        Map<Integer, List<Task>> out = new HashMap<>();
        if (projectIds.isEmpty()) {
            return out;
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            if (!isTaskTableAvailable(cnx)) {
                return out;
            }
            String sql = "SELECT id, project_id, status, weight, created_at " +
                    "FROM task WHERE project_id IN (" + placeholders(projectIds.size()) + ") " +
                    "ORDER BY project_id ASC, created_at ASC, id ASC";
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                bindIds(ps, projectIds);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Task task = new Task();
                        task.setId(rs.getInt("id"));
                        task.setProjectId(rs.getInt("project_id"));
                        task.setStatus(TaskStatus.fromDb(rs.getString("status")));
                        task.setWeight(rs.getInt("weight"));
                        task.setCreatedAt(rs.getTimestamp("created_at"));
                        out.computeIfAbsent(task.getProjectId(), k -> new ArrayList<>()).add(task);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement tasks PBS: " + e.getMessage(), e);
        }
        return out;
    }

    private DecisionData loadDecisionData(Set<Integer> projectIds) {
        DecisionData out = new DecisionData();
        if (projectIds.isEmpty()) {
            return out;
        }
        String sql = "SELECT idProj, dateDecision, StatutD FROM Decisions " +
                "WHERE idProj IN (" + placeholders(projectIds.size()) + ") " +
                "ORDER BY idProj ASC, dateDecision ASC, idD ASC";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            bindIds(ps, projectIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("idProj");
                    Timestamp ts = rs.getTimestamp("dateDecision");
                    if (ts != null) {
                        out.datesByProject.computeIfAbsent(projectId, k -> new ArrayList<>())
                                .add(ts.toLocalDateTime());
                    }
                    DecisionStatus status = DecisionStatus.fromDb(rs.getString("StatutD"));
                    if (status == DecisionStatus.REFUSED) {
                        out.refusedProjects.add(projectId);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement decisions PBS: " + e.getMessage(), e);
        }

        return out;
    }

    private boolean isTaskTableAvailable(Connection cnx) throws SQLException {
        if (taskTableAvailableCache != null) {
            return taskTableAvailableCache;
        }
        String sql = "SELECT 1 FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name = 'task' LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            taskTableAvailableCache = rs.next();
            return taskTableAvailableCache;
        }
    }

    private void bindIds(PreparedStatement ps, Set<Integer> ids) throws SQLException {
        int i = 1;
        for (Integer id : ids) {
            ps.setInt(i++, id);
        }
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(Math.max(1, count), "?"));
    }

    private int totalWeeks(LocalDate from, LocalDate to) {
        LocalDate startWeek = weekStart(from);
        LocalDate endWeek = weekStart(to);
        int weeks = 0;
        for (LocalDate d = startWeek; !d.isAfter(endWeek); d = d.plusWeeks(1)) {
            weeks++;
        }
        return Math.max(1, weeks);
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private int weekKey(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        int y = date.get(wf.weekBasedYear());
        int w = date.get(wf.weekOfWeekBasedYear());
        return (y * 100) + w;
    }

    private LocalDate toLocalDate(Timestamp ts, LocalDate fallback) {
        if (ts == null) return fallback;
        return ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private TaskStatus safeStatus(Task t) {
        return t.getStatus() == null ? TaskStatus.TODO : t.getStatus();
    }

    private double clamp100(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private LocalDate nowDate() {
        return LocalDate.now(clock);
    }

    private static class DecisionData {
        private final Map<Integer, List<LocalDateTime>> datesByProject = new HashMap<>();
        private final Set<Integer> refusedProjects = new HashSet<>();
    }
}
