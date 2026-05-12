package com.advisora.Services.strategie;

import com.advisora.Model.strategie.Notification;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import com.advisora.utils.MyConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationManager {

    private static NotificationManager instance;

    private final ObservableList<Notification> notifications;
    private Boolean notificationScopeColumnsAvailableCache = null;
    private String notificationDateColumnCache = null;
    private Boolean notificationEventColumnsAvailableCache = null;

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

    public static synchronized NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }

    public void addNotification(Notification notification) {
        notification.setTimestamp(LocalDateTime.now());

        if (SessionContext.getCurrentRole() == UserRole.ADMIN ||
                SessionContext.getCurrentRole() == UserRole.GERANT) {
            notifications.add(0, notification);
        }

        System.out.println("ADD NOTIFICATION CALLED: " + notification.getTitle());

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildInsertSql(cnx, false))) {

            bindNotificationInsert(ps, cnx, notification.getTitle(), notification.getMessage(), null, null);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur insertion notification: " + e.getMessage(), e);
        }

        if (soundEnabled && ding != null &&
                (SessionContext.getCurrentRole() == UserRole.ADMIN ||
                        SessionContext.getCurrentRole() == UserRole.GERANT)) {
            ding.play();
        }
    }

    public void createIfNotExists(String title, String message, UserRole targetRole, Integer targetProjectId) {
        if (title == null || title.isBlank() || message == null || message.isBlank()) return;

        boolean scoped = targetRole != null || targetProjectId != null;
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildCheckSql(cnx, scoped))) {

            ps.setString(1, title.trim());
            ps.setString(2, message.trim());

            if (scoped && hasNotificationScopeColumns(cnx)) {
                if (targetRole == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, targetRole.name());
                }
                if (targetProjectId == null) {
                    ps.setNull(4, Types.INTEGER);
                } else {
                    ps.setInt(4, targetProjectId);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur verification notification: " + e.getMessage(), e);
        }

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildInsertSql(cnx, scoped))) {

            bindNotificationInsert(ps, cnx, title.trim(), message.trim(), targetRole, targetProjectId);

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
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement("SELECT * FROM notification" + buildOrderClause(cnx));
             ResultSet rs = ps.executeQuery()) {

            notifications.clear();
            while (rs.next()) {
                notifications.add(mapNotification(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement notifications: " + e.getMessage(), e);
        }
    }

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

    public void loadNotificationsForRole(UserRole role) throws SQLException {
        loadNotificationsForUser(role, null);
    }

    public void loadNotificationsForUser(UserRole role, Integer userId) throws SQLException {
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildLoadByRoleSql(cnx, role, userId))) {

            if (role == UserRole.CLIENT && userId != null && hasNotificationScopeColumns(cnx)) {
                ps.setInt(1, userId);
            } else if (role != null && hasNotificationScopeColumns(cnx)) {
                ps.setString(1, role.name());
            }

            try (ResultSet rs = ps.executeQuery()) {
                notifications.clear();
                while (rs.next()) {
                    notifications.add(mapNotification(rs));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur chargement notifications pour role " + role + ": " + e.getMessage(), e);
        }
    }

    private Notification mapNotification(ResultSet rs) throws SQLException {
        Notification n = new Notification(
                rs.getString("title"),
                rs.getString("description")
        );
        n.setRead(rs.getBoolean("isRead"));
        n.setTimestamp(readNotificationTimestamp(rs));
        return n;
    }

    private String buildInsertSql(Connection cnx, boolean scoped) throws SQLException {
        String dateColumn = getNotificationDateColumn(cnx);
        boolean hasEventColumns = hasNotificationEventColumns(cnx);
        if (scoped && hasNotificationScopeColumns(cnx)) {
            if (dateColumn != null && hasEventColumns) {
                return "INSERT INTO notification (title, description, eventType, spokenText, " + dateColumn + ", isRead, target_role, target_project_id) VALUES (?, ?, ?, ?, NOW(), FALSE, ?, ?)";
            }
            if (dateColumn != null) {
                return "INSERT INTO notification (title, description, " + dateColumn + ", isRead, target_role, target_project_id) VALUES (?, ?, NOW(), FALSE, ?, ?)";
            }
            if (hasEventColumns) {
                return "INSERT INTO notification (title, description, eventType, spokenText, isRead, target_role, target_project_id) VALUES (?, ?, ?, ?, FALSE, ?, ?)";
            }
            return "INSERT INTO notification (title, description, isRead, target_role, target_project_id) VALUES (?, ?, FALSE, ?, ?)";
        }
        if (dateColumn != null && hasEventColumns) {
            return "INSERT INTO notification (title, description, eventType, spokenText, " + dateColumn + ", isRead) VALUES (?, ?, ?, ?, NOW(), FALSE)";
        }
        if (dateColumn != null) {
            return "INSERT INTO notification (title, description, " + dateColumn + ", isRead) VALUES (?, ?, NOW(), FALSE)";
        }
        if (hasEventColumns) {
            return "INSERT INTO notification (title, description, eventType, spokenText, isRead) VALUES (?, ?, ?, ?, FALSE)";
        }
        return "INSERT INTO notification (title, description, isRead) VALUES (?, ?, FALSE)";
    }

    private String buildCheckSql(Connection cnx, boolean scoped) throws SQLException {
        String dateFilter = buildCurrentDateFilter(cnx);
        if (scoped && hasNotificationScopeColumns(cnx)) {
            return """
                    SELECT 1
                    FROM notification
                    WHERE title = ?
                      AND description = ?
                      AND (target_role <=> ?)
                      AND (target_project_id <=> ?)
                    """ + dateFilter + """
                    LIMIT 1
                    """;
        }
        return """
                SELECT 1
                FROM notification
                WHERE title = ?
                  AND description = ?
                """ + dateFilter + """
                LIMIT 1
                """;
    }

    private String buildLoadByRoleSql(Connection cnx, UserRole role, Integer userId) throws SQLException {
        String orderClause = buildOrderClause(cnx);
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
                    """ + buildOrderClauseForAlias(cnx, "n");
        }
        if (role != null && hasNotificationScopeColumns(cnx)) {
            return """
                    SELECT *
                    FROM notification
                    WHERE target_role IS NULL OR target_role = ?
                    """ + orderClause;
        }
        return "SELECT * FROM notification" + orderClause;
    }

    private boolean hasNotificationScopeColumns(Connection cnx) throws SQLException {
        if (notificationScopeColumnsAvailableCache != null) {
            return notificationScopeColumnsAvailableCache;
        }
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

    private String buildCurrentDateFilter(Connection cnx) throws SQLException {
        String dateColumn = getNotificationDateColumn(cnx);
        if (dateColumn == null) {
            return "";
        }
        return "  AND DATE(" + dateColumn + ") = CURDATE()\n";
    }

    private String buildOrderClause(Connection cnx) throws SQLException {
        String dateColumn = getNotificationDateColumn(cnx);
        return dateColumn == null ? "" : " ORDER BY " + dateColumn + " DESC";
    }

    private String buildOrderClauseForAlias(Connection cnx, String alias) throws SQLException {
        String dateColumn = getNotificationDateColumn(cnx);
        return dateColumn == null ? "" : "ORDER BY " + alias + "." + dateColumn + " DESC";
    }

    private String getNotificationDateColumn(Connection cnx) throws SQLException {
        if (notificationDateColumnCache != null) {
            return notificationDateColumnCache;
        }
        if (columnExists(cnx, "dateNotification")) {
            notificationDateColumnCache = "dateNotification";
            return notificationDateColumnCache;
        }
        if (columnExists(cnx, "created_at")) {
            notificationDateColumnCache = "created_at";
            return notificationDateColumnCache;
        }
        if (columnExists(cnx, "createdAt")) {
            notificationDateColumnCache = "createdAt";
            return notificationDateColumnCache;
        }
        return null;
    }

    private LocalDateTime readNotificationTimestamp(ResultSet rs) throws SQLException {
        try {
            Timestamp ts = rs.getTimestamp("dateNotification");
            if (ts != null) {
                return ts.toLocalDateTime();
            }
        } catch (SQLException ignored) {
        }

        try {
            Date d = rs.getDate("dateNotification");
            if (d != null) {
                return d.toLocalDate().atStartOfDay();
            }
        } catch (SQLException ignored) {
        }

        try {
            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) {
                return ts.toLocalDateTime();
            }
        } catch (SQLException ignored) {
        }

        try {
            Timestamp ts = rs.getTimestamp("createdAt");
            if (ts != null) {
                return ts.toLocalDateTime();
            }
        } catch (SQLException ignored) {
        }

        return LocalDate.now().atStartOfDay();
    }

    private boolean hasNotificationEventColumns(Connection cnx) throws SQLException {
        if (notificationEventColumnsAvailableCache != null) {
            return notificationEventColumnsAvailableCache;
        }
        notificationEventColumnsAvailableCache =
                columnExists(cnx, "eventType") && columnExists(cnx, "spokenText");
        return notificationEventColumnsAvailableCache;
    }

    private void bindNotificationInsert(
            PreparedStatement ps,
            Connection cnx,
            String title,
            String message,
            UserRole targetRole,
            Integer targetProjectId
    ) throws SQLException {
        int index = 1;
        ps.setString(index++, title);
        ps.setString(index++, message);

        if (hasNotificationEventColumns(cnx)) {
            ps.setString(index++, "GENERAL");
            ps.setString(index++, message);
        }

        if (targetRole != null || targetProjectId != null) {
            if (targetRole == null) {
                ps.setNull(index++, Types.VARCHAR);
            } else {
                ps.setString(index++, targetRole.name());
            }
            if (targetProjectId == null) {
                ps.setNull(index, Types.INTEGER);
            } else {
                ps.setInt(index, targetProjectId);
            }
        }
    }

    private boolean columnExists(Connection cnx, String column) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'notification'
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
