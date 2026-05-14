package com.advisora.Services.strategie;

import com.advisora.Model.strategie.SWOTItem;
import com.advisora.enums.SWOTType;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class serviceSWOT {
    public List<SWOTItem> getByStrategie(int strategieId) {
        String sql = "SELECT id, strategie_id, type, description, weight, created_at, updated_at " +
                "FROM swot_item WHERE strategie_id=? ORDER BY type, id DESC";
        List<SWOTItem> list = new ArrayList<>();

        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, strategieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SWOTItem it = new SWOTItem();
                    it.setId(rs.getInt("id"));
                    it.setStrategieId(rs.getInt("strategie_id"));
                    it.setType(SWOTType.valueOf(rs.getString("type")));
                    it.setDescription(rs.getString("description"));
                    int w = rs.getInt("weight");
                    it.setWeight(rs.wasNull() ? null : w);

                    Timestamp ca = rs.getTimestamp("created_at");
                    Timestamp ua = rs.getTimestamp("updated_at");
                    it.setCreatedAt(ca == null ? null : ca.toLocalDateTime());
                    it.setUpdatedAt(ua == null ? null : ua.toLocalDateTime());

                    list.add(it);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Map<SWOTType, List<SWOTItem>> getGroupedByStrategie(int strategieId) {
        Map<SWOTType, List<SWOTItem>> map = new EnumMap<>(SWOTType.class);
        for (SWOTType t : SWOTType.values()) map.put(t, new ArrayList<>());

        for (SWOTItem it : getByStrategie(strategieId)) {
            map.get(it.getType()).add(it);
        }
        return map;
    }

    public void add(SWOTItem item) throws SQLException {
        String sql = "INSERT INTO swot_item(strategie_id, type, description, weight) VALUES(?,?,?,?)";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, item.getStrategieId());
            ps.setString(2, item.getType().name());
            ps.setString(3, item.getDescription());
            if (item.getWeight() == null) ps.setNull(4, Types.TINYINT);
            else ps.setInt(4, item.getWeight());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) item.setId(keys.getInt(1));
            }
        }
    }

    public void update(SWOTItem item) throws SQLException {
        String sql = "UPDATE swot_item SET type=?, description=?, weight=? WHERE id=?";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, item.getType().name());
            ps.setString(2, item.getDescription());
            if (item.getWeight() == null) ps.setNull(3, Types.TINYINT);
            else ps.setInt(3, item.getWeight());
            ps.setInt(4, item.getId());

            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM swot_item WHERE id=?";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
    public void addMany(List<SWOTItem> items) throws SQLException {
        if (items == null || items.isEmpty()) return;

        String sql = "INSERT INTO swot_item(strategie_id, type, description, weight) VALUES(?,?,?,?)";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            for (SWOTItem item : items) {
                ps.setInt(1, item.getStrategieId());
                ps.setString(2, item.getType().name());
                ps.setString(3, item.getDescription());
                if (item.getWeight() == null) ps.setNull(4, Types.TINYINT);
                else ps.setInt(4, item.getWeight());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    public void deleteAllByStrategie(int strategieId) throws SQLException {
        String sql = "DELETE FROM swot_item WHERE strategie_id=?";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, strategieId);
            ps.executeUpdate();
        }
    }

    public int countByStrategie(int strategieId) {
        String sql = "SELECT COUNT(*) FROM swot_item WHERE strategie_id=?";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, strategieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;}
}
