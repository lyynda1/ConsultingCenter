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
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationManager {

    private static NotificationManager instance;

    private final ObservableList<Notification> notifications;
    private Boolean notificationScopeColumnsAvailableCache = null;
    private String notificationDateColumnCache = null;
    private Boolean notificationEventColumnsAvailableCache = null;
    private Boolean notificationTargetRoleColumnAvailableCache = null;
    private Boolean notificationTargetProjectColumnAvailableCache = null;

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
             PreparedStatement ps = cnx.prepareStatement(buildInsertSql(cnx, null, null))) {

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

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildCheckSql(cnx, targetRole, targetProjectId))) {

            int checkIndex = 1;
            ps.setString(checkIndex++, title.trim());
            ps.setString(checkIndex++, message.trim());
            if (targetRole != null && hasTargetRoleColumn(cnx)) {
                ps.setString(checkIndex++, targetRole.name());
            }
            if (targetProjectId != null && hasTargetProjectColumn(cnx)) {
                ps.setInt(checkIndex, targetProjectId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur verification notification: " + e.getMessage(), e);
        }

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(buildInsertSql(cnx, targetRole, targetProjectId))) {

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

    private String buildInsertSql(Connection cnx, UserRole targetRole, Integer targetProjectId) throws SQLException {
        String dateColumn = getNotificationDateColumn(cnx);
        boolean hasEventColumns = hasNotificationEventColumns(cnx);

        boolean includeRole = targetRole != null && hasTargetRoleColumn(cnx);
        boolean includeProject = targetProjectId != null && hasTargetProjectColumn(cnx);

        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();

        columns.add("title");
        values.add("?");
        columns.add("description");
        values.add("?");

        if (hasEventColumns) {
            columns.add("eventType");
            values.add("?");
            columns.add("spokenText");
            values.add("?");
        }

        if (dateColumn != null) {
            columns.add(dateColumn);
            values.add("NOW()");
        }

        columns.add("isRead");
        values.add("FALSE");

        if (includeRole) {
            columns.add("target_role");
            values.add("?");
        }
        if (includeProject) {
            columns.add("target_project_id");
            values.add("?");
        }

        return "INSERT INTO notification (" + String.join(", ", columns) + ") VALUES (" + String.join(", ", values) + ")";
    }

    private String buildCheckSql(Connection cnx, UserRole targetRole, Integer targetProjectId) throws SQLException {
        String dateFilter = buildCurrentDateFilter(cnx);
        StringBuilder sql = new StringBuilder("""
                SELECT 1
                FROM notification
                WHERE title = ?
                  AND description = ?
                """);
        if (targetRole != null && hasTargetRoleColumn(cnx)) {
            sql.append("  AND (target_role <=> ?)\n");
        }
        if (targetProjectId != null && hasTargetProjectColumn(cnx)) {
            sql.append("  AND (target_project_id <=> ?)\n");
        }
        sql.append(dateFilter);
        sql.append("LIMIT 1\n");
        return sql.toString();
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
        if (!hasTargetRoleColumn(cnx) || !hasTargetProjectColumn(cnx)) {
            notificationScopeColumnsAvailableCache = false;
            return false;
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

    private boolean hasTargetRoleColumn(Connection cnx) throws SQLException {
        if (notificationTargetRoleColumnAvailableCache != null) {
            return notificationTargetRoleColumnAvailableCache;
        }
        notificationTargetRoleColumnAvailableCache = columnExists(cnx, "target_role");
        return notificationTargetRoleColumnAvailableCache;
    }

    private boolean hasTargetProjectColumn(Connection cnx) throws SQLException {
        if (notificationTargetProjectColumnAvailableCache != null) {
            return notificationTargetProjectColumnAvailableCache;
        }
        notificationTargetProjectColumnAvailableCache = columnExists(cnx, "target_project_id");
        return notificationTargetProjectColumnAvailableCache;
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

        if (targetRole != null && hasTargetRoleColumn(cnx)) {
            ps.setString(index++, targetRole.name());
        }
        if (targetProjectId != null && hasTargetProjectColumn(cnx)) {
            ps.setInt(index, targetProjectId);
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
