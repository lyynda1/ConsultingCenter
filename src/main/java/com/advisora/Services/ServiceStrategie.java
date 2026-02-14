package com.advisora.Services;

import com.advisora.Model.Project;
import com.advisora.Model.Strategie;
import com.advisora.Util.DB;
import com.advisora.enums.StrategyStatut;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServiceStrategie implements IService<Strategie> {

    @Override
    public void ajouter(Strategie strategie) {
        String sql = "INSERT INTO `strategies`( `versions`, `statusStrategie`, `CreatedAtS`, `lockedAt`, `news`, `nomStrategie`)" +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {


            ps.setDouble(1, strategie.getVersion());
            ps.setString(2, strategie.getStatut().name());
            ps.setTimestamp(3, strategie.getCreatedAt() != null ? Timestamp.valueOf(strategie.getCreatedAt()) : null);
            ps.setTimestamp(4, strategie.getLockedAt() != null ? Timestamp.valueOf(strategie.getLockedAt()) : null);
            ps.setString(5, strategie.getNews());
            ps.setString(6, strategie.getNomStrategie());


            ps.executeUpdate();

            // Get generated ID
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    strategie.setId(rs.getInt(1));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error adding strategy: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Strategie> afficher() {
        List<Strategie> strategies = new ArrayList<>();
        String sql = "SELECT * FROM strategies";

        try (Connection conn = DB.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Strategie strategie = mapResultSetToStrategie(rs);
                strategies.add(strategie);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching strategies: " + e.getMessage(), e);
        }

        return strategies;
    }

    @Override
    public void modifier(Strategie strategie) {
        String sql = "UPDATE strategies SET nomStrategie=?, versions=?, statusStrategie=?, createdAtS=?, lockedAt=? " +
                     "WHERE idStrategie=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, strategie.getNomStrategie());
            ps.setDouble(2, strategie.getVersion());
            ps.setString(3, strategie.getStatut().name());
            ps.setTimestamp(4, strategie.getCreatedAt() != null ? Timestamp.valueOf(strategie.getCreatedAt()) : null);
            ps.setTimestamp(5, strategie.getLockedAt() != null ? Timestamp.valueOf(strategie.getLockedAt()) : null);


            ps.setInt(6, strategie.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error updating strategy: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(Strategie strategie) {
        String sql = "DELETE FROM strategies WHERE idStrategie=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, strategie.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting strategy: " + e.getMessage(), e);
        }
    }

    // Helper method to map ResultSet to Strategie
    private Strategie mapResultSetToStrategie(ResultSet rs) throws SQLException {
        int id = rs.getInt("idStrategie");
        String nom = rs.getString("nomStrategie");
        int version = rs.getInt("versions");
        String statutStr = rs.getString("statusStrategie");
        StrategyStatut statut = StrategyStatut.valueOf(statutStr);

        Timestamp createdAtTs = rs.getTimestamp("CreatedAtS");
        LocalDateTime createdAt = createdAtTs != null ? createdAtTs.toLocalDateTime() : null;

        Timestamp lockedAtTs = rs.getTimestamp("lockedAt");
        LocalDateTime lockedAt = lockedAtTs != null ? lockedAtTs.toLocalDateTime() : null;

        String news = rs.getString("news");
        Project projet = null;
        return new Strategie(id,nom, version, statut, createdAt, lockedAt, news,projet);
    }

    // Additional helper method to get a strategy by ID
    public Strategie getById(String nomStrategie) {
        String sql = "SELECT * FROM strategies WHERE nomStrategie=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nomStrategie);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToStrategie(rs);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching strategy by ID: " + e.getMessage(), e);
        }

        return null;
    }

    // Additional helper method to get strategies by status
    public List<Strategie> getByStatut(StrategyStatut statut) {
        List<Strategie> strategies = new ArrayList<>();
        String sql = "SELECT * FROM strategies WHERE statusStrategie=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, statut.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    strategies.add(mapResultSetToStrategie(rs));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching strategies by status: " + e.getMessage(), e);
        }

        return strategies;
    }
}
