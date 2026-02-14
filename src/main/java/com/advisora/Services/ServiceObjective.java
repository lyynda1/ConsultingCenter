package com.advisora.Services;

import com.advisora.Model.Objective;
import com.advisora.Util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceObjective implements IService<Objective> {

    // ✅ CHANGE THESE if your table/columns differ
    private static final String TABLE = "objectives";
    private static final String COL_ID = "idOb";
    private static final String COL_NOM_OBJECTIVE = "nomObj";
    private static final String COL_DESC = "descriptionOb";
    private static final String COL_PRIORITY = "priorityOb";
    private static final String COL_STRATEGIE_ID = "ids";

    @Override
    public void ajouter(Objective o) {
        String sql = "INSERT INTO " + TABLE + " (" + COL_STRATEGIE_ID + ", " + COL_NOM_OBJECTIVE + ", " + COL_DESC + ", " + COL_PRIORITY + ") " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, o.getStrategieId());
            ps.setString(2, o.getNomObjective());
            ps.setString(3, o.getDescription());
            ps.setInt(4, o.getPriority());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) o.setId(rs.getInt(1));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error adding objective: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Objective> afficher() {
        String sql = "SELECT * FROM " + TABLE + " ORDER BY " + COL_PRIORITY + " ASC";
        List<Objective> list = new ArrayList<>();

        try (Connection conn = DB.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) list.add(map(rs));

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching objectives: " + e.getMessage(), e);
        }

        return list;
    }

    // ✅ Recommended: list objectives for one strategie
    public List<Objective> afficherByStrategie(int strategieId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE " + COL_STRATEGIE_ID + "=? ORDER BY " + COL_PRIORITY + " ASC";
        List<Objective> list = new ArrayList<>();

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, strategieId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching objectives by strategie: " + e.getMessage(), e);
        }

        return list;
    }

    @Override

    public void modifier(Objective o) {
        String sql = "UPDATE " + TABLE +
                " SET " + COL_STRATEGIE_ID + "=?, " +
                COL_NOM_OBJECTIVE + "=?, " +
                COL_DESC + "=?, " +
                COL_PRIORITY + "=? " +
                "WHERE " + COL_ID + "=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, o.getStrategieId());
            ps.setString(2, o.getNomObjective());
            ps.setString(3, o.getDescription());
            ps.setInt(4, o.getPriority());
            ps.setInt(5, o.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error updating objective: " + e.getMessage(), e);
        }
    }


    @Override
    public void supprimer(Objective o) {
        String sql = "DELETE FROM " + TABLE + " WHERE " + COL_ID + "=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, o.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error deleting objective: " + e.getMessage(), e);
        }
    }

    private Objective map(ResultSet rs) throws SQLException {
        Objective o = new Objective();
        o.setId(rs.getInt(COL_ID));
        o.setStrategieId(rs.getInt(COL_STRATEGIE_ID));
        o.setDescription(rs.getString(COL_DESC));
        o.setPriority(rs.getInt(COL_PRIORITY));
        o.setNomObjective(rs.getString(COL_NOM_OBJECTIVE));
        return o;
    }
}
