package com.advisora.Services.projet;

import com.advisora.Model.projet.Task;
import com.advisora.enums.TaskStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TaskService {

    public void addTask(Task task) {
        validate(task, true);
        String sql = "INSERT INTO task (project_id, title, status, weight, created_at) VALUES (?, ?, ?, ?, NOW())";
        run(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, task.getProjectId());
                ps.setString(2, task.getTitle().trim());
                ps.setString(3, safeStatus(task).name());
                ps.setInt(4, task.getWeight());
                ps.executeUpdate();
            }
        });
    }

    public void updateTask(Task task) {
        validate(task, false);
        String sql = "UPDATE task SET title=?, status=?, weight=? WHERE id=?";
        run(() -> {
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setString(1, task.getTitle().trim());
                ps.setString(2, safeStatus(task).name());
                ps.setInt(3, task.getWeight());
                ps.setInt(4, task.getId());
                ps.executeUpdate();
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
            String sql = "SELECT id, project_id, title, status, weight, created_at FROM task WHERE project_id=? ORDER BY created_at DESC, id DESC";
            List<Task> list = new ArrayList<>();
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                }
            }
            return list;
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

    private TaskStatus safeStatus(Task task) {
        return task.getStatus() == null ? TaskStatus.TODO : task.getStatus();
    }

    private void validate(Task task, boolean create) {
        if (task == null) throw new IllegalArgumentException("task null");
        if (task.getProjectId() <= 0) throw new IllegalArgumentException("project_id invalide");
        if (task.getTitle() == null || task.getTitle().isBlank()) throw new IllegalArgumentException("title requis");
        if (task.getWeight() < 1) throw new IllegalArgumentException("weight doit etre >= 1");
        if (!create && task.getId() <= 0) throw new IllegalArgumentException("id task invalide");
    }

    private Task map(ResultSet rs) throws SQLException {
        Task t = new Task();
        t.setId(rs.getInt("id"));
        t.setProjectId(rs.getInt("project_id"));
        t.setTitle(rs.getString("title"));
        t.setStatus(TaskStatus.fromDb(rs.getString("status")));
        t.setWeight(rs.getInt("weight"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        return t;
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
