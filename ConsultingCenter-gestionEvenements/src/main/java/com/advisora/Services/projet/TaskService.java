package com.advisora.Services.projet;

import com.advisora.Model.projet.Task;
import com.advisora.Services.strategie.NotificationManager;
import com.advisora.enums.TaskStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaskService {
    private final Clock clock;
    private Boolean durationColumnsAvailableCache = null;

    public TaskService() {
        this(Clock.systemDefaultZone());
    }

    public TaskService(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public void addTask(Task task) {
        validate(task, true);
        run(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                ensureDurationColumns(cnx);
                boolean hasDurationCols = hasDurationColumns(cnx);
                String sql = hasDurationCols
                        ? "INSERT INTO task (project_id, title, status, weight, duration_days, last_warning_date, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())"
                        : "INSERT INTO task (project_id, title, status, weight, created_at) VALUES (?, ?, ?, ?, NOW())";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setInt(1, task.getProjectId());
                    ps.setString(2, task.getTitle().trim());
                    ps.setString(3, safeStatus(task).name());
                    ps.setInt(4, task.getWeight());
                    if (hasDurationCols) {
                        ps.setInt(5, task.getDurationDays());
                        ps.setDate(6, task.getLastWarningDate());
                    }
                    ps.executeUpdate();
                }
            }
        });
    }

    public void updateTask(Task task) {
        validate(task, false);
        run(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                ensureDurationColumns(cnx);
                boolean hasDurationCols = hasDurationColumns(cnx);
                String sql = hasDurationCols
                        ? "UPDATE task SET title=?, status=?, weight=?, duration_days=?, last_warning_date=? WHERE id=?"
                        : "UPDATE task SET title=?, status=?, weight=? WHERE id=?";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setString(1, task.getTitle().trim());
                    ps.setString(2, safeStatus(task).name());
                    ps.setInt(3, task.getWeight());
                    if (hasDurationCols) {
                        ps.setInt(4, task.getDurationDays());
                        ps.setDate(5, task.getLastWarningDate());
                        ps.setInt(6, task.getId());
                    } else {
                        ps.setInt(4, task.getId());
                    }
                    ps.executeUpdate();
                }
            }
        });
    }

    public void deleteTask(int taskId) {
        if (taskId <= 0) throw new IllegalArgumentException("id task invalide");
        String sql = "DELETE FROM task WHERE id=?";
        run(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, taskId);
                ps.executeUpdate();
            }
        });
    }

    public List<Task> getByProject(int projectId) {
        if (projectId <= 0) throw new IllegalArgumentException("project_id invalide");
        return call(() -> {
            List<Task> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                ensureDurationColumns(cnx);
                boolean hasDurationCols = hasDurationColumns(cnx);
                String sql = hasDurationCols
                        ? "SELECT id, project_id, title, status, weight, duration_days, last_warning_date, created_at FROM task WHERE project_id=? ORDER BY created_at DESC, id DESC"
                        : "SELECT id, project_id, title, status, weight, created_at FROM task WHERE project_id=? ORDER BY created_at DESC, id DESC";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setInt(1, projectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(map(rs, hasDurationCols));
                        }
                    }
                }
            }
            return list;
        });
    }

    public void checkAndNotifyNearFinishTasks(int projectId) {
        if (projectId <= 0) throw new IllegalArgumentException("project_id invalide");
        if (!isDurationColumnsAvailable()) {
            return;
        }
        LocalDate today = today();
        String projectTitle = loadProjectTitle(projectId);
        List<Task> tasks = getByProject(projectId);
        for (Task task : tasks) {
            if (shouldNotifyNearFinish(today, task)) {
                LocalDate dueDate = computeDueDate(toLocalDate(task.getCreatedAt(), today), task.getDurationDays());
                String status = safeStatus(task).name().toLowerCase(Locale.ROOT);
                String title = "Alerte tache proche de fin";
                String message = "Projet #" + projectId + " (" + projectTitle + "), tache '" + safe(task.getTitle()) +
                        "' statut=" + status +
                        ", fin prevue=" + dueDate +
                        ". Verifier avec le client si cette tache est finie.";
                NotificationManager.getInstance().createIfNotExists(title, message, null, projectId);
                updateTaskWarningDate(task.getId(), today);
                task.setLastWarningDate(Date.valueOf(today));
            }
        }
    }

    public void checkAndNotifyNearFinishAllProjects() {
        call(() -> {
            String sql = "SELECT DISTINCT project_id FROM task";
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int projectId = rs.getInt("project_id");
                    if (projectId > 0) {
                        checkAndNotifyNearFinishTasks(projectId);
                    }
                }
            }
            return null;
        });
    }

    public void checkAndNotifyNearFinishForClientProjects(int clientId) {
        if (clientId <= 0) throw new IllegalArgumentException("client_id invalide");
        call(() -> {
            String sql = """
                    SELECT DISTINCT t.project_id
                    FROM task t
                    JOIN Projects p ON p.idProj = t.project_id
                    WHERE p.idClient = ?
                    """;
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int projectId = rs.getInt("project_id");
                        if (projectId > 0) {
                            checkAndNotifyNearFinishTasks(projectId);
                        }
                    }
                }
            }
            return null;
        });
    }

    public double computeProjectProgress(int projectId) {
        if (projectId <= 0) throw new IllegalArgumentException("project_id invalide");
        return call(() -> {
            String sql = "SELECT " +
                    "COALESCE(SUM(CASE WHEN status='DONE' THEN weight ELSE 0 END), 0) AS done_weight, " +
                    "COALESCE(SUM(weight), 0) AS total_weight " +
                    "FROM task WHERE project_id=?";
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0.0;
                    double done = rs.getDouble("done_weight");
                    double total = rs.getDouble("total_weight");
                    if (total <= 0) return 0.0;
                    return (done / total) * 100.0;
                }
            }
        });
    }

    public int seedDemoTasksForNotifications() {
        return call(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                ensureDurationColumns(cnx);
                Integer projectId = firstProjectId(cnx);
                if (projectId == null) return 0;

                int inserted = 0;
                inserted += insertDemoTaskIfMissing(cnx, projectId, "[DEMO ALERT] Analyse client", "TODO", 3, 8, 10);
                inserted += insertDemoTaskIfMissing(cnx, projectId, "[DEMO ALERT] Validation budget", "IN_PROGRESS", 2, 7, 9);
                inserted += insertDemoTaskIfMissing(cnx, projectId, "[DEMO ALERT] Revue risques", "TODO", 1, 6, 8);
                return inserted;
            }
        });
    }

    private TaskStatus safeStatus(Task task) {
        return task.getStatus() == null ? TaskStatus.TODO : task.getStatus();
    }

    private Integer firstProjectId(Connection cnx) throws SQLException {
        String sql = "SELECT idProj FROM Projects ORDER BY idProj LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("idProj") : null;
        }
    }

    private int insertDemoTaskIfMissing(Connection cnx, int projectId, String title, String status, int weight, int durationDays, int createdDaysAgo) throws SQLException {
        String checkSql = "SELECT 1 FROM task WHERE project_id=? AND title=? LIMIT 1";
        try (PreparedStatement check = cnx.prepareStatement(checkSql)) {
            check.setInt(1, projectId);
            check.setString(2, title);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return 0;
            }
        }

        String insertSql = """
                INSERT INTO task(project_id, title, status, weight, duration_days, last_warning_date, created_at)
                VALUES (?, ?, ?, ?, ?, NULL, DATE_SUB(NOW(), INTERVAL ? DAY))
                """;
        try (PreparedStatement ins = cnx.prepareStatement(insertSql)) {
            ins.setInt(1, projectId);
            ins.setString(2, title);
            ins.setString(3, status);
            ins.setInt(4, weight);
            ins.setInt(5, durationDays);
            ins.setInt(6, Math.max(1, createdDaysAgo));
            return ins.executeUpdate();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void validate(Task task, boolean create) {
        if (task == null) throw new IllegalArgumentException("task null");
        if (task.getProjectId() <= 0) throw new IllegalArgumentException("project_id invalide");
        if (task.getTitle() == null || task.getTitle().isBlank()) throw new IllegalArgumentException("title requis");
        if (task.getWeight() < 1) throw new IllegalArgumentException("weight doit etre >= 1");
        if (task.getDurationDays() < 1) throw new IllegalArgumentException("duration_days doit etre >= 1");
        if (!create && task.getId() <= 0) throw new IllegalArgumentException("id task invalide");
    }

    private Task map(ResultSet rs, boolean hasDurationColumns) throws SQLException {
        Task t = new Task();
        t.setId(rs.getInt("id"));
        t.setProjectId(rs.getInt("project_id"));
        t.setTitle(rs.getString("title"));
        t.setStatus(TaskStatus.fromDb(rs.getString("status")));
        t.setWeight(rs.getInt("weight"));
        if (hasDurationColumns) {
            t.setDurationDays(rs.getInt("duration_days"));
            t.setLastWarningDate(rs.getDate("last_warning_date"));
        } else {
            t.setDurationDays(1);
            t.setLastWarningDate(null);
        }
        t.setCreatedAt(rs.getTimestamp("created_at"));
        return t;
    }

    static LocalDate computeLastQuarterStart(LocalDate startDate, int durationDays) {
        if (startDate == null) {
            return null;
        }
        int safeDuration = Math.max(1, durationDays);
        int startOffset = (int) Math.ceil(safeDuration * 0.75d);
        return startDate.plusDays(startOffset);
    }

    static LocalDate computeDueDate(LocalDate startDate, int durationDays) {
        if (startDate == null) {
            return null;
        }
        return startDate.plusDays(Math.max(1, durationDays));
    }

    boolean shouldNotifyNearFinish(LocalDate today, Task task) {
        if (task == null || today == null) return false;
        if (safeStatus(task) == TaskStatus.DONE) return false;

        LocalDate created = toLocalDate(task.getCreatedAt(), today);
        LocalDate lastQuarterStart = computeLastQuarterStart(created, task.getDurationDays());
        if (lastQuarterStart == null || today.isBefore(lastQuarterStart)) return false;

        Date lastWarn = task.getLastWarningDate();
        return lastWarn == null || !today.equals(lastWarn.toLocalDate());
    }

    private LocalDate toLocalDate(Timestamp ts, LocalDate fallback) {
        if (ts == null) return fallback;
        return ts.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private boolean isDurationColumnsAvailable() {
        return call(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection()) {
                ensureDurationColumns(cnx);
                return hasDurationColumns(cnx);
            }
        });
    }

    private boolean hasDurationColumns(Connection cnx) throws SQLException {
        if (durationColumnsAvailableCache != null) {
            return durationColumnsAvailableCache;
        }
        String sql = "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'task' " +
                "AND column_name IN ('duration_days','last_warning_date')";
        int found = 0;
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found++;
            }
        }
        durationColumnsAvailableCache = found == 2;
        return durationColumnsAvailableCache;
    }

    private void ensureDurationColumns(Connection cnx) throws SQLException {
        if (hasDurationColumns(cnx)) {
            return;
        }
        if (!columnExists(cnx, "duration_days")) {
            try (PreparedStatement ps = cnx.prepareStatement(
                    "ALTER TABLE task ADD COLUMN duration_days INT NOT NULL DEFAULT 1 AFTER weight")) {
                ps.executeUpdate();
            }
        }
        if (!columnExists(cnx, "last_warning_date")) {
            try (PreparedStatement ps = cnx.prepareStatement(
                    "ALTER TABLE task ADD COLUMN last_warning_date DATE NULL AFTER duration_days")) {
                ps.executeUpdate();
            }
        }
        durationColumnsAvailableCache = null;
        hasDurationColumns(cnx);
    }

    private boolean columnExists(Connection cnx, String columnName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = ? LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String loadProjectTitle(int projectId) {
        String sql = "SELECT titleProj FROM Projects WHERE idProj=? LIMIT 1";
        return call(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "Projet";
                    String title = rs.getString("titleProj");
                    return title == null || title.isBlank() ? "Projet" : title.trim();
                }
            }
        });
    }

    private void updateTaskWarningDate(int taskId, LocalDate date) {
        if (taskId <= 0 || date == null) return;
        if (!isDurationColumnsAvailable()) return;
        String sql = "UPDATE task SET last_warning_date=? WHERE id=?";
        run(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(date));
                ps.setInt(2, taskId);
                ps.executeUpdate();
            }
        });
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

    @FunctionalInterface
    private interface SqlRun {
        void exec() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T exec() throws SQLException;
    }
}
