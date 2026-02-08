package com.advisora.Services;

import com.advisora.Util.DB;
import com.advisora.entity.*;
import com.advisora.enums.UserRole;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceUser implements IService<User> {

    private UserRole resolveRole(User u) {
        if (u.getRole() != null) return u.getRole();
        if (u instanceof Client) return UserRole.CLIENT;
        if (u instanceof Gerant) return UserRole.MANAGER;
        return UserRole.ADMIN;
    }

    @Override
    public void ajouter(User u) {
        String sqlUser = """
            INSERT INTO users (email, password, nom, prenom, num_tel, role, dateN)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection cnx = DB.getConnection();
             PreparedStatement ps = cnx.prepareStatement(sqlUser, Statement.RETURN_GENERATED_KEYS)) {

            UserRole role = resolveRole(u);
            u.setRole(role);

            ps.setString(1, u.getEmail());
            ps.setString(2, u.getPassword());
            ps.setString(3, u.getName());
            ps.setString(4, u.getFirstName());
            ps.setString(5, u.getPhoneNumber());
            ps.setString(6, role.name());
            ps.setString(7, u.getDateN());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) u.setId(keys.getInt(1));
            }

            // Insert into subclass table (idC/idG/idA = users.id)
            if (u instanceof Client c) {
                String sqlClient = "INSERT INTO client (idC, budget, description) VALUES (?, ?, ?)";
                try (PreparedStatement ps2 = cnx.prepareStatement(sqlClient)) {
                    ps2.setInt(1, u.getId());
                    ps2.setDouble(2, c.getBudget());
                    ps2.setString(3, c.getDescription());
                    ps2.executeUpdate();
                }
            } else if (u instanceof Gerant g) {
                String sqlGerant = "INSERT INTO gerants (idG, expertise_area) VALUES (?, ?)";
                try (PreparedStatement ps2 = cnx.prepareStatement(sqlGerant)) {
                    ps2.setInt(1, u.getId());
                    ps2.setString(2, g.getExpertiseArea());
                    ps2.executeUpdate();
                }
            } else { // Admin
                String sqlAdmin = "INSERT INTO admin (idA) VALUES (?)";
                try (PreparedStatement ps2 = cnx.prepareStatement(sqlAdmin)) {
                    ps2.setInt(1, u.getId());
                    ps2.executeUpdate();
                }
            }

            System.out.println("✅ User added with id=" + u.getId() + " role=" + role);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<User> afficher() {
        List<User> users = new ArrayList<>();

        String sql = """
            SELECT u.*,
                   c.budget, c.description,
                   g.expertise_area
            FROM users u
            LEFT JOIN client c ON c.idC = u.id
            LEFT JOIN gerants g ON g.idG = u.id
        """;

        try (Connection cnx = DB.getConnection();
             Statement st = cnx.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String email = rs.getString("email");
                String pwd = rs.getString("password");
                String nom = rs.getString("nom");
                String prenom = rs.getString("prenom");
                String phone = rs.getString("num_tel");
                String dateN = rs.getString("dateN");
                UserRole role = UserRole.valueOf(rs.getString("role"));

                if (role == UserRole.CLIENT) {
                    double budget = rs.getDouble("budget");
                    String description = rs.getString("description");
                    users.add(new Client(id, email, pwd, nom, prenom, phone, dateN, budget, description));
                } else if (role == UserRole.MANAGER) {
                    String expertise = rs.getString("expertise_area");
                    users.add(new Gerant(id, email, pwd, nom, prenom, phone, dateN,  expertise));
                    // ⚠️ if your Gerant constructor doesn’t take role, remove role param
                } else {
                    users.add(new Admin(id, email, pwd, nom, prenom, phone, dateN));
                    // ⚠️ same: depends on your Admin constructor
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return users;
    }

    @Override
    public void modifier(User u) {

        String sqlUser = """
        UPDATE users
        SET email=?, password=?, nom=?, prenom=?, num_tel=?, role=?, dateN=?
        WHERE id=?
    """;

        try (Connection cnx = DB.getConnection()) {

            UserRole role = resolveRole(u);
            u.setRole(role);

            // ✅ 1) UPDATE users + check if user exists
            int updated;
            try (PreparedStatement ps = cnx.prepareStatement(sqlUser)) {
                ps.setString(1, u.getEmail());
                ps.setString(2, u.getPassword());
                ps.setString(3, u.getName());
                ps.setString(4, u.getFirstName());
                ps.setString(5, u.getPhoneNumber());
                ps.setString(6, role.name());
                ps.setString(7, u.getDateN());
                ps.setInt(8, u.getId());

                updated = ps.executeUpdate();
            }

            // ✅ if id doesn't exist -> stop (prevents FK error)
            if (updated == 0) {
                throw new SQLException("User not found in users table for id=" + u.getId()
                        + ". Did you forget to call ajouter() first or is id=0?");
            }

            // ✅ 2) CLEAN other child tables (so only one role table remains)
            if (role == UserRole.CLIENT) {
                supprimerRowSiExiste(cnx, "gerants", "idG", u.getId());
                supprimerRowSiExiste(cnx, "admin", "idA", u.getId());
            } else if (role == UserRole.MANAGER) {
                supprimerRowSiExiste(cnx, "client", "idC", u.getId());
                supprimerRowSiExiste(cnx, "admin", "idA", u.getId());
            } else { // ADMIN
                supprimerRowSiExiste(cnx, "client", "idC", u.getId());
                supprimerRowSiExiste(cnx, "gerants", "idG", u.getId());
            }

            // ✅ 3) UPSERT into the correct child table
            if (role == UserRole.CLIENT) {
                if (!(u instanceof Client c)) {
                    throw new IllegalArgumentException("To set role CLIENT, you must pass a Client object (same id).");
                }

                String upd = "UPDATE client SET budget=?, description=? WHERE idC=?";
                try (PreparedStatement ps2 = cnx.prepareStatement(upd)) {
                    ps2.setDouble(1, c.getBudget());
                    ps2.setString(2, c.getDescription());
                    ps2.setInt(3, u.getId());

                    int rows = ps2.executeUpdate();
                    if (rows == 0) {
                        String ins = "INSERT INTO client (idC, budget, description) VALUES (?, ?, ?)";
                        try (PreparedStatement ps3 = cnx.prepareStatement(ins)) {
                            ps3.setInt(1, u.getId());
                            ps3.setDouble(2, c.getBudget());
                            ps3.setString(3, c.getDescription());
                            ps3.executeUpdate();
                        }
                    }
                }

            } else if (role == UserRole.MANAGER) {
                if (!(u instanceof Gerant g)) {
                    throw new IllegalArgumentException("To set role MANAGER, you must pass a Gerant object (same id).");
                }

                String upd = "UPDATE gerants SET expertise_area=? WHERE idG=?";
                try (PreparedStatement ps2 = cnx.prepareStatement(upd)) {
                    ps2.setString(1, g.getExpertiseArea());
                    ps2.setInt(2, u.getId());

                    int rows = ps2.executeUpdate();
                    if (rows == 0) {
                        String ins = "INSERT INTO gerants (idG, expertise_area) VALUES (?, ?)";
                        try (PreparedStatement ps3 = cnx.prepareStatement(ins)) {
                            ps3.setInt(1, u.getId());
                            ps3.setString(2, g.getExpertiseArea());
                            ps3.executeUpdate();
                        }
                    }
                }

            } else { // ADMIN
                // If admin table has only idA
                String upd = "UPDATE admin SET idA=idA WHERE idA=?";
                try (PreparedStatement ps2 = cnx.prepareStatement(upd)) {
                    ps2.setInt(1, u.getId());

                    int rows = ps2.executeUpdate();
                    if (rows == 0) {
                        String ins = "INSERT INTO admin (idA) VALUES (?)";
                        try (PreparedStatement ps3 = cnx.prepareStatement(ins)) {
                            ps3.setInt(1, u.getId());
                            ps3.executeUpdate();
                        }
                    }
                }
            }

            System.out.println("✅ Updated user id=" + u.getId() + " new role=" + role);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void supprimer(User u) {
        String sql = "DELETE FROM users WHERE id=?";

        try (Connection cnx = DB.getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {

            ps.setInt(1, u.getId());
            int rows = ps.executeUpdate();

            if (rows > 0) System.out.println("✅ Deleted user id=" + u.getId());
            else System.out.println("❌ No user found id=" + u.getId());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void supprimerRowSiExiste(Connection cnx, String table, String pkCol, int userId) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE " + pkCol + "=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate(); // if not exists -> 0 rows, no problem
        }
    }

}
