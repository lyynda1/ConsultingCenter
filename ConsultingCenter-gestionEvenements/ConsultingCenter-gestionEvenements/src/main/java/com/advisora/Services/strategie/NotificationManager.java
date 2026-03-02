package com.advisora.Services.strategie;

import com.advisora.Model.strategie.Notification;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import com.advisora.utils.MyConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationManager {

    private static NotificationManager instance;

    private final ObservableList<Notification> notifications;

    // Optional: cache for whether the DB has the extra scope columns
    private Boolean notificationScopeColumnsAvailableCache = null;

    // ==========================================================
    // Sound
    // ==========================================================
    private AudioClip ding;
    private boolean soundEnabled = true;

    public NotificationManager() {
        notifications = FXCollections.observableArrayList();

        try {
            URL url = getClass().getResource("/Assets/notify.wav");
            if (url == null) {
                System.err.println("Sound file not found: /Assets/notify.wav");
                soundEnabled = false;
                return;
            }

            ding = new AudioClip(url.toExternalForm());
            ding.setVolume(0.8);

        } catch (Exception ex) {
            System.err.println("Audio disabled: " + ex.getMessage());
            soundEnabled = false;
            ding = null;
        }
    }

    /* ---------- Singleton ---------- */
    public static synchronized NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    /* ---------- Accessors ---------- */
    public ObservableList<Notification> getNotifications() {
        return notifications;
    }

    /* ---------- Notification creation (generic) ---------- */
    public void addNotification(Notification notification) {
        notification.setTimestamp(LocalDateTime.now());

        // Add to in‑memory list (visible only to Admin/Gerant per original logic)
        if (SessionContext.getCurrentRole() == UserRole.ADMIN ||
                SessionContext.getCurrentRole() == UserRole.GERANT) {
            notifications.add(0, notification);
        }

        System.out.println("ADD NOTIFICATION CALLED: " + notification.getTitle());

        // Persist to DB
        String sql = "INSERT INTO notification (title, description, dateNotification, isRead) VALUES (?, ?, NOW(), FALSE)";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.setString(1, notification.getTitle());
            ps.setString(2, notification.getMessage());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur insertion notification: " + e.getMessage(), e);
        }

        // Play sound
        if (soundEnabled && ding != null &&
                (SessionContext.getCurrentRole() == UserRole.ADMIN ||
                        SessionContext.getCurrentRole() == UserRole.GERANT)) {
            ding.play();
        }
    }

    /* ---------- Helper: create if not exists (with optional scope) ---------- */
    public void createIfNotExists(String title, String message, UserRole targetRole, Integer targetProjectId) {
        if (title == null || title.isBlank() || message == null || message.isBlank()) return;

        boolean scoped = targetRole != null || targetProjectId != null;
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildCheckSql(cnx, scoped))) {

            ps.setString(1, title.trim());
            ps.setString(2, message.trim());

            if (scoped && hasNotificationScopeColumns(cnx)) {
                if (targetRole == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, targetRole.name());
                }
                if (targetProjectId == null) {
                    ps.setNull(4, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(4, targetProjectId);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;  // already exists
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur verification notification: " + e.getMessage(), e);
        }

        // Insert the new notification (scoped or not)
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildInsertSql(cnx, scoped))) {

            ps.setString(1, title.trim());
            ps.setString(2, message.trim());
            if (scoped && hasNotificationScopeColumns(cnx)) {
                if (targetRole == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, targetRole.name());
                }
                if (targetProjectId == null) {
                    ps.setNull(4, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(4, targetProjectId);
                }
            }

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur insertion notification: " + e.getMessage(), e);
        }
    }

    public void createIfNotExists(String title, String message) {
        createIfNotExists(title, message, null, null);
    }

    public void createIfNotExists(String title, String message, UserRole targetRole) {
        createIfNotExists(title, message, targetRole, null);
    }

    /* ---------- Sound control ---------- */
    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    /* ---------- Mark as read ---------- */
    public void markAsRead(Notification notification) {
        notification.setRead(true);
    }

    /* ---------- Counters, UI helpers ---------- */
    public int getUnreadCount() {
        return (int) notifications.stream().filter(n -> !n.isRead()).count();
    }

    public void notifyChanged() {
        notifications.setAll(List.copyOf(notifications));
    }

    /* ---------- Load notifications ---------- */
    public void loadNotifications() {
        String sql = "SELECT * FROM notification ORDER BY dateNotification DESC";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            notifications.clear();
            while (rs.next()) {
                Notification n = new Notification(
                        rs.getString("title"),
                        rs.getString("description")
                );
                n.setRead(rs.getBoolean("isRead"));
                n.setTimestamp(rs.getTimestamp("dateNotification").toLocalDateTime());
                notifications.add(n);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement notifications: " + e.getMessage(), e);
        }
    }

    /* ---------- Clear all notifications ---------- */
    public void clearAllNotifications() {
        String sql = "DELETE FROM notification";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.executeUpdate();
            notifications.clear();
            notifyChanged();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression notifications: " + e.getMessage(), e);
        }
    }

    /* ---------- Load for role / user ---------- */
    public void loadNotificationsForRole(UserRole role) throws SQLException {
        loadNotificationsForUser(role, null);
    }

    public void loadNotificationsForUser(UserRole role, Integer userId) throws SQLException {
        String sql = buildLoadByRoleSql(MyConnection.getInstance().getConnection(), role, userId);
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            // bind parameters depending on the query that was built
            if (role == UserRole.CLIENT && userId != null && hasNotificationScopeColumns(cnx)) {
                ps.setInt(1, userId);
            } else if (role != null && hasNotificationScopeColumns(cnx)) {
                ps.setString(1, role.name());
            }

            try (ResultSet rs = ps.executeQuery()) {
                notifications.clear();
                while (rs.next()) {
                    Notification n = new Notification(
                            rs.getString("title"),
                            rs.getString("description")
                    );
                    n.setRead(rs.getBoolean("isRead"));
                    n.setTimestamp(rs.getTimestamp("dateNotification").toLocalDateTime());
                    notifications.add(n);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement notifications pour rôle " + role + ": " + e.getMessage(), e);
        }
    }

    /* ---------- SQL builders (helpers) ---------- */
    private String buildInsertSql(Connection cnx, boolean scoped) throws SQLException {
        if (scoped && hasNotificationScopeColumns(cnx)) {
            return "INSERT INTO notification (title, description, dateNotification, isRead, target_role, target_project_id) VALUES (?, ?, NOW(), FALSE, ?, ?)";
        }
        return "INSERT INTO notification (title, description, dateNotification, isRead) VALUES (?, ?, NOW(), FALSE)";
    }

    private String buildCheckSql(Connection cnx, boolean scoped) throws SQLException {
        if (scoped && hasNotificationScopeColumns(cnx)) {
            return """
                    SELECT 1
                    FROM notification
                    WHERE title = ?
                      AND description = ?
                      AND (target_role <=> ?)
                      AND (target_project_id <=> ?)
                      AND DATE(dateNotification) = CURDATE()
                    LIMIT 1
                    """;
        }
        return """
                SELECT 1
                FROM notification
                WHERE title = ?
                  AND description = ?
                  AND DATE(dateNotification) = CURDATE()
                LIMIT 1
                """;
    }

    private String buildLoadByRoleSql(Connection cnx, UserRole role, Integer userId) throws SQLException {
        if (role == UserRole.CLIENT && userId != null && hasNotificationScopeColumns(cnx)) {
            return """
                    SELECT n.*
                    FROM notification n
                    LEFT JOIN projects p ON p.idProj = n.target_project_id
                    WHERE (
                        n.target_project_id IS NOT NULL
                        AND p.idClient = ?
                    ) OR (
                        n.target_project_id IS NULL
                        AND n.target_role = 'CLIENT'
                    )
                    ORDER BY n.dateNotification DESC
                    """;
        }
        if (role != null && hasNotificationScopeColumns(cnx)) {
            return """
                    SELECT *
                    FROM notification
                    WHERE target_role IS NULL OR target_role = ?
                    ORDER BY dateNotification DESC
                    """;
        }
        return "SELECT * FROM notification ORDER BY dateNotification DESC";
    }

    /* ---------- Column existence helpers ---------- */
    private boolean hasNotificationScopeColumns(Connection cnx) throws SQLException {
        if (notificationScopeColumnsAvailableCache != null) {
            return notificationScopeColumnsAvailableCache;
        }
        ensureNotificationScopeColumns(cnx);
        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'notification'
                  AND column_name IN ('target_role', 'target_project_id')
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int count = 0;
            while (rs.next()) count++;
            notificationScopeColumnsAvailableCache = count == 2;
            return notificationScopeColumnsAvailableCache;
        }
    }

    private void ensureNotificationScopeColumns(Connection cnx) throws SQLException {
        if (!columnExists(cnx, "target_role")) {
            try (PreparedStatement alter = cnx.prepareStatement(
                    "ALTER TABLE notification ADD COLUMN target_role VARCHAR(20) NULL")) {
                alter.executeUpdate();
            }
        }
        if (!columnExists(cnx, "target_project_id")) {
            try (PreparedStatement alter = cnx.prepareStatement(
                    "ALTER TABLE notification ADD COLUMN target_project_id INT NULL")) {
                alter.executeUpdate();
            }
        }
    }

    private boolean columnExists(Connection cnx, String column) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name   = 'notification'
                  AND column_name = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
