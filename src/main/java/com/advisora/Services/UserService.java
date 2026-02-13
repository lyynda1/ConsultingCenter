package com.advisora.Services;

import com.advisora.Model.User;
import com.advisora.Util.DB;
import com.advisora.enums.UserRole;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class UserService implements IService<User> {

    @Override
    public void ajouter(User u) {
        String sql = "INSERT INTO `user` " +
                "(cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser, roleUser, expertiseAreaUser) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // cin est VARCHAR(20) dans ta DB
            ps.setString(1, String.valueOf(u.getCIN())); // si tu gardes CIN int dans ton Model
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPassword());
            ps.setString(4, u.getNom());
            ps.setString(5, u.getPrenom());
            ps.setString(6, u.getNumTel());

            // dateNUser est DATE
            // Si u.getDateN() est String "YYYY-MM-DD"
            if (u.getDateN() == null || u.getDateN().isBlank()) {
                ps.setNull(7, Types.DATE);
            } else {
                ps.setDate(7, Date.valueOf(u.getDateN()));
            }

            // roleUser enum('client','gerant','admin')
            // ton enum Java doit matcher OU tu convertis
            ps.setString(8, u.getRole() != null ? toDbRole(u.getRole()) : "client");

            ps.setString(9, u.getExpertiseArea());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) u.setId(rs.getInt(1)); // idUser auto_increment
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout user: " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> afficher() {
        String sql = "SELECT idUser, cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser, roleUser, expertiseAreaUser " +
                "FROM `user`";

        List<User> list = new ArrayList<>();

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = new User();

                u.setId(rs.getInt("idUser"));

                // cin est VARCHAR dans DB -> ton Model a int, donc conversion :

                u.setCin(rs.getString("cin"));
                u.setEmail(rs.getString("EmailUser"));
                u.setPassword(rs.getString("passwordUser"));
                u.setNom(rs.getString("nomUser"));
                u.setPrenom(rs.getString("PrenomUser"));
                u.setNumTel(rs.getString("NumTelUser"));

                Date d = rs.getDate("dateNUser");
                u.setDateN(d != null ? d.toString() : null); // "YYYY-MM-DD"

                String roleDb = rs.getString("roleUser");
                u.setRole(roleDb != null ? fromDbRole(roleDb) : null);

                u.setExpertiseArea(rs.getString("expertiseAreaUser"));

                list.add(u);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur afficher users: " + e.getMessage(), e);
        }

        return list;
    }

    @Override
    public void modifier(User u) {
        String sql = "UPDATE `user` SET " +
                "cin=?, EmailUser=?, passwordUser=?, nomUser=?, PrenomUser=?, NumTelUser=?, dateNUser=?, roleUser=?, expertiseAreaUser=? " +
                "WHERE idUser=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, String.valueOf(u.getCIN()));
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPassword());
            ps.setString(4, u.getNom());
            ps.setString(5, u.getPrenom());
            ps.setString(6, u.getNumTel());

            if (u.getDateN() == null || u.getDateN().isBlank()) {
                ps.setNull(7, Types.DATE);
            } else {
                ps.setDate(7, Date.valueOf(u.getDateN()));
            }

            ps.setString(8, u.getRole() != null ? toDbRole(u.getRole()) : "client");
            ps.setString(9, u.getExpertiseArea());

            ps.setInt(10, u.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur modifier user: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(User u) {
        supprimerParId(u.getId());
    }

    public void supprimerParId(int id) {
        String sql = "DELETE FROM `user` WHERE idUser=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur supprimer user: " + e.getMessage(), e);
        }
    }

    // --- helpers role: adapte selon ton enum Java ---
    private String toDbRole(UserRole role) {
        // Si ton enum est CLIENT/GERANT/ADMIN => convertir en minuscule pour la DB
        return role.name().toLowerCase();
    }

    private UserRole fromDbRole(String dbRole) {
        // dbRole = "client" / "gerant" / "admin"
        return UserRole.valueOf(dbRole.toUpperCase());
    }
}
