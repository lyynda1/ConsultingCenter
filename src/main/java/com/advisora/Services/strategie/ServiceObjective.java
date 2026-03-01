package com.advisora.Services.strategie;

import com.advisora.Model.strategie.Objective;
import com.advisora.Services.IService;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceObjective implements IService<Objective> {

    @Override
    public void ajouter(Objective objective) {
        validate(objective, true);
        String sql = "INSERT INTO objectives (descriptionOb, priorityOb, ids, nomObj) VALUES (?, ?, ?, ?)";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, objective.getDescription());
            ps.setInt(2, objective.getPriority());
            ps.setInt(3, objective.getStrategieId());
            ps.setString(4, objective.getNomObjective());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    objective.setId(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout objectif: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Objective> afficher() {
        String sql = "SELECT idOb, descriptionOb, priorityOb, ids, nomObj FROM objectives ORDER BY idOb DESC";
        List<Objective> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture objectifs: " + e.getMessage(), e);
        }
    }

    @Override
    public void modifier(Objective objective) {
        validate(objective, false);
        String sql = "UPDATE objectives SET descriptionOb=?, priorityOb=?, ids=?, nomObj=? WHERE idOb=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, objective.getDescription());
            ps.setInt(2, objective.getPriority());
            ps.setInt(3, objective.getStrategieId());
            ps.setString(4, objective.getNomObjective());
            ps.setInt(5, objective.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification objectif: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(Objective objective) {
        if (objective == null || objective.getId() <= 0) {
            throw new IllegalArgumentException("Objectif invalide.");
        }
        String sql = "DELETE FROM objectives WHERE idOb=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, objective.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression objectif: " + e.getMessage(), e);
        }
    }

    public List<Objective> getByStrategieId(int idStrategie) {
        String sql = "SELECT idOb, descriptionOb, priorityOb, ids, nomObj FROM objectives WHERE ids=? ORDER BY idOb ASC";
        List<Objective> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idStrategie);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture objectifs strategie: " + e.getMessage(), e);
        }
    }

    private Objective map(ResultSet rs) throws SQLException {
        Objective o = new Objective();
        o.setId(rs.getInt("idOb"));
        o.setDescription(rs.getString("descriptionOb"));
        o.setPriority(rs.getInt("priorityOb"));
        o.setStrategieId(rs.getInt("ids"));
        o.setNomObjective(rs.getString("nomObj"));
        return o;
    }

    private void validate(Objective o, boolean create) {
        if (o == null) throw new IllegalArgumentException("Objectif obligatoire.");
        if (o.getNomObjective() == null || o.getNomObjective().isBlank()) throw new IllegalArgumentException("Nom objectif obligatoire.");
        if (o.getDescription() == null || o.getDescription().isBlank()) throw new IllegalArgumentException("Description objectif obligatoire.");
        if (o.getPriority() < 0) throw new IllegalArgumentException("Priorite >= 0 obligatoire.");
        if (o.getStrategieId() <= 0) throw new IllegalArgumentException("Strategie obligatoire.");
        if (!create && o.getId() <= 0) throw new IllegalArgumentException("idObjectif invalide.");
    }
}


