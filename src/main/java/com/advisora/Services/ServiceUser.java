package com.advisora.Services;

import com.advisora.Model.*;
import com.advisora.Util.DB;
import com.advisora.enums.UserRole;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceUser implements IService<User> {

    @Override
    public void ajouter(User user) {
        String sql = "INSERT INTO users (email, password, name, firstName, phoneNumber, dateN, role, budget, description, expertiseArea) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getFirstName());
            ps.setString(5, user.getPhoneNumber());
            ps.setString(6, user.getDateN());
            ps.setString(7, user.getRole().name());

            // Role-specific fields
            if (user instanceof Client) {
                Client client = (Client) user;
                ps.setDouble(8, client.getBudget());
                ps.setString(9, client.getDescription());
                ps.setNull(10, Types.VARCHAR);
            } else if (user instanceof Gerant) {
                Gerant gerant = (Gerant) user;
                ps.setNull(8, Types.DOUBLE);
                ps.setNull(9, Types.VARCHAR);
                ps.setString(10, gerant.getExpertiseArea());
            } else {
                // Admin
                ps.setNull(8, Types.DOUBLE);
                ps.setNull(9, Types.VARCHAR);
                ps.setNull(10, Types.VARCHAR);
            }

            ps.executeUpdate();

            // Get generated ID
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setId(rs.getInt(1));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error adding user: " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> afficher() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";

        try (Connection conn = DB.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                User user = mapResultSetToUser(rs);
                if (user != null) {
                    users.add(user);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching users: " + e.getMessage(), e);
        }

        return users;
    }

    @Override
    public void modifier(User user) {
        String sql = "UPDATE users SET email=?, password=?, name=?, firstName=?, phoneNumber=?, " +
                     "dateN=?, role=?, budget=?, description=?, expertiseArea=? WHERE id=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getFirstName());
            ps.setString(5, user.getPhoneNumber());
            ps.setString(6, user.getDateN());
            ps.setString(7, user.getRole().name());

            // Role-specific fields
            if (user instanceof Client) {
                Client client = (Client) user;
                ps.setDouble(8, client.getBudget());
                ps.setString(9, client.getDescription());
                ps.setNull(10, Types.VARCHAR);
            } else if (user instanceof Gerant) {
                Gerant gerant = (Gerant) user;
                ps.setNull(8, Types.DOUBLE);
                ps.setNull(9, Types.VARCHAR);
                ps.setString(10, gerant.getExpertiseArea());
            } else {
                // Admin
                ps.setNull(8, Types.DOUBLE);
                ps.setNull(9, Types.VARCHAR);
                ps.setNull(10, Types.VARCHAR);
            }

            ps.setInt(11, user.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error updating user: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(User user) {
        String sql = "DELETE FROM users WHERE id=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, user.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting user: " + e.getMessage(), e);
        }
    }

    // Helper method to map ResultSet to appropriate User subclass
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String email = rs.getString("email");
        String password = rs.getString("password");
        String name = rs.getString("name");
        String firstName = rs.getString("firstName");
        String phoneNumber = rs.getString("phoneNumber");
        String dateN = rs.getString("dateN");
        String roleStr = rs.getString("role");

        UserRole role = UserRole.valueOf(roleStr);

        switch (role) {
            case CLIENT:
                double budget = rs.getDouble("budget");
                String description = rs.getString("description");
                return new Client(id, email, password, name, firstName, phoneNumber, dateN, budget, description);

            case MANAGER:
                String expertiseArea = rs.getString("expertiseArea");
                return new Gerant(id, email, password, name, firstName, phoneNumber, dateN, expertiseArea);

            case ADMIN:
                return new Admin(id, email, password, name, firstName, phoneNumber, dateN);

            default:
                return null;
        }
    }
}
