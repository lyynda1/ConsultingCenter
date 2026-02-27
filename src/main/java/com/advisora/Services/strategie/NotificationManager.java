package com.advisora.Services.strategie;

import com.advisora.Model.strategie.Notification;
import com.advisora.enums.UserRole;
import com.advisora.utils.MyConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationManager {
    private static NotificationManager instance;
    private final ObservableList<Notification> notifications;
    private Boolean audienceColumnsAvailableCache = null;

    // ✅ sound
    private final AudioClip ding;
    private boolean soundEnabled = true;

    private NotificationManager() {
        notifications = FXCollections.observableArrayList();

        URL url = getClass().getResource("/Assets/notify.wav");
        if (url == null) {
            throw new IllegalStateException("Sound file not found: /Assets/notify.wav");
        }
        ding = new AudioClip(url.toExternalForm());
        ding.setVolume(0.8); // 0..1
    }

    public static synchronized NotificationManager getInstance() {
        if (instance == null) instance = new NotificationManager();
        return instance;
    }

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }

    public void addNotification(Notification notification) {
        addNotification(notification, null);
    }

    public void addNotification(Notification notification, UserRole targetRole) {
        if (notification == null) return;
        notification.setTimestamp(LocalDateTime.now());
        notifications.add(0, notification);
        System.out.println("ADD NOTIFICATION CALLED: " + notification.getTitle());

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildInsertSql(cnx, targetRole != null))) {
            ps.setString(1, notification.getTitle());
            ps.setString(2, notification.getMessage());
            if (hasAudienceColumns(cnx)) {
                if (targetRole == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, targetRole.name());
                }
            }
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur insertion notification: " + e.getMessage(), e);
        }

        if (soundEnabled) {
            ding.play();
        }
    }

    public void createIfNotExists(String title, String message) {
        createIfNotExists(title, message, null);
    }

    public void createIfNotExists(String title, String message, UserRole targetRole) {
        if (title == null || title.isBlank() || message == null || message.isBlank()) return;

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildCheckSql(cnx, targetRole != null))) {
            ps.setString(1, title.trim());
            ps.setString(2, message.trim());
            if (hasAudienceColumns(cnx)) {
                if (targetRole == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, targetRole.name());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur verification notification: " + e.getMessage(), e);
        }

        addNotification(new Notification(title.trim(), message.trim()), targetRole);
    }


    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
    }

    public void markAsRead(Notification notification) {
        notification.setRead(true);
    }

    public int getUnreadCount() {
        return (int) notifications.stream().filter(n -> !n.isRead()).count();
    }

    public void notifyChanged() {
        notifications.setAll(List.copyOf(notifications));
    }

    public void loadNotifications() {
        // ✅ load from DB
        String sql = "SELECT * FROM notification ORDER BY dateNotification DESC";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             var rs = ps.executeQuery()) {
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

    public void clearAllNotifications() {
        String sql = "DELETE FROM notification"; // adjust table name if it's "notifications"

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.executeUpdate();
            notifications.clear();        // clear in-memory list
            notifyChanged();              // if you rely on listeners elsewhere

        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression notifications: " + e.getMessage(), e);
        }
    }

    public void loadNotificationsForRole(UserRole role) {
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildLoadByRoleSql(cnx, role));
             ) {
            if (role != null && hasAudienceColumns(cnx)) {
                ps.setString(1, role.name());
            }
            try (var rs = ps.executeQuery()) {

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
            throw new RuntimeException(e);
        }
    }

    private String buildInsertSql(Connection cnx, boolean withRole) throws SQLException {
        if (withRole && hasAudienceColumns(cnx)) {
            return "INSERT INTO notification (title, description, dateNotification, isRead, target_role) VALUES (?, ?, NOW(), FALSE, ?)";
        }
        return "INSERT INTO notification (title, description, dateNotification, isRead) VALUES (?, ?, NOW(), FALSE)";
    }

    private String buildCheckSql(Connection cnx, boolean withRole) throws SQLException {
        if (withRole && hasAudienceColumns(cnx)) {
            return """
                    SELECT 1
                    FROM notification
                    WHERE title = ?
                      AND description = ?
                      AND target_role = ?
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

    private String buildLoadByRoleSql(Connection cnx, UserRole role) throws SQLException {
        if (role != null && hasAudienceColumns(cnx)) {
            return """
                    SELECT * FROM notification
                    WHERE target_role IS NULL OR target_role = ?
                    ORDER BY dateNotification DESC
                    """;
        }
        return "SELECT * FROM notification ORDER BY dateNotification DESC";
    }

    private boolean hasAudienceColumns(Connection cnx) throws SQLException {
        if (audienceColumnsAvailableCache != null) {
            return audienceColumnsAvailableCache;
        }
        ensureAudienceColumns(cnx);
        String sql = "SELECT 1 FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'notification' " +
                "AND column_name = 'target_role' LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            audienceColumnsAvailableCache = rs.next();
            return audienceColumnsAvailableCache;
        }
    }

    private void ensureAudienceColumns(Connection cnx) throws SQLException {
        String checkSql = "SELECT 1 FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'notification' " +
                "AND column_name = 'target_role' LIMIT 1";
        try (PreparedStatement check = cnx.prepareStatement(checkSql);
             ResultSet rs = check.executeQuery()) {
            if (rs.next()) {
                return;
            }
        }
        try (PreparedStatement alter = cnx.prepareStatement(
                "ALTER TABLE notification ADD COLUMN target_role VARCHAR(20) NULL AFTER isRead")) {
            alter.executeUpdate();
        }
    }


}
