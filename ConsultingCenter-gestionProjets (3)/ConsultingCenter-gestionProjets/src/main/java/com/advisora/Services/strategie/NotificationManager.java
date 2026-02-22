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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationManager {
    private static NotificationManager instance;
    private final ObservableList<Notification> notifications;

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

        notification.setTimestamp(LocalDateTime.now());
        if (SessionContext.getCurrentRole() == UserRole.ADMIN || SessionContext.getCurrentRole() == UserRole.GERANT) {
            notifications.add(0, notification);
        }
        System.out.println("ADD NOTIFICATION CALLED: " + notification.getTitle());


        String sql = "INSERT INTO notification (title, description, dateNotification, isRead) VALUES (?, ?, NOW(), FALSE)";

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.setString(1, notification.getTitle());
            ps.setString(2, notification.getMessage());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur insertion notification: " + e.getMessage(), e);
        }

        if (soundEnabled) {
            if (SessionContext.getCurrentRole() == UserRole.ADMIN || SessionContext.getCurrentRole() == UserRole.GERANT) {
                ding.play();
            }

        }
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

        if (role != UserRole.ADMIN && role != UserRole.GERANT) {
            return; // DO NOT clear list
        }

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
            throw new RuntimeException(e);
        }
    }


}
