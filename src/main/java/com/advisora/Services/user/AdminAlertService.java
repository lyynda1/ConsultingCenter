package com.advisora.Services.user;

import com.advisora.Services.strategie.NotificationManager;
import com.advisora.enums.UserRole;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminAlertService {

    private final NotificationManager notif = NotificationManager.getInstance();

    public void scanInactiveManagers() {

        String sql = """
            SELECT EmailUser, nomUser, PrenomUser
            FROM `user`
            WHERE roleUser='gerant'
              AND (last_activity_at IS NULL OR last_activity_at < DATE_SUB(NOW(), INTERVAL 4 DAY))
        """;

        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String email = rs.getString("EmailUser");
                String nom = rs.getString("nomUser");
                String prenom = rs.getString("PrenomUser");

                String title = "Inactivite gerant";
                String desc = "Le gerant " + nom + " " + prenom + " (" + email + ") est inactif depuis 4 jours.";

                notif.createIfNotExists(title, desc, UserRole.ADMIN);
            }

            notif.notifyChanged();

        } catch (SQLException e) {
            throw new RuntimeException("scanInactiveManagers failed: " + e.getMessage(), e);
        }
    }
}

