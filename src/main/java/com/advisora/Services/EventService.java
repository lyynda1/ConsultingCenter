package com.advisora.Services;

import com.advisora.Model.Event;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EventService {

    public void add(Event e) {
        validate(e, true);
        String sql = "INSERT INTO events (titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getTitleEv());
            ps.setString(2, e.getDescriptionEv());
            ps.setTimestamp(3, Timestamp.valueOf(e.getStartDateEv()));
            ps.setTimestamp(4, Timestamp.valueOf(e.getEndDateEv()));
            ps.setString(5, e.getOrganisateurName());
            ps.setInt(6, e.getCapaciteEvnt());
            ps.setString(7, e.getLocalisationEv());
            if (e.getIdGerant() != null && e.getIdGerant() > 0) {
                ps.setInt(8, e.getIdGerant());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    e.setIdEv(rs.getInt(1));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur ajout evenement: " + ex.getMessage(), ex);
        }
    }

    public void update(Event e) {
        validate(e, false);
        String sql = "UPDATE events SET titleEv=?, descriptionEv=?, startDateEv=?, endDateEv=?, organisateurName=?, capaciteEvnt=?, localisationEv=?, idGerant=? WHERE idEv=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, e.getTitleEv());
            ps.setString(2, e.getDescriptionEv());
            ps.setTimestamp(3, Timestamp.valueOf(e.getStartDateEv()));
            ps.setTimestamp(4, Timestamp.valueOf(e.getEndDateEv()));
            ps.setString(5, e.getOrganisateurName());
            ps.setInt(6, e.getCapaciteEvnt());
            ps.setString(7, e.getLocalisationEv());
            if (e.getIdGerant() != null && e.getIdGerant() > 0) {
                ps.setInt(8, e.getIdGerant());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
            ps.setInt(9, e.getIdEv());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur modification evenement: " + ex.getMessage(), ex);
        }
    }

    public void delete(int idEv) {
        if (idEv <= 0) {
            throw new IllegalArgumentException("idEv invalide.");
        }
        String sql = "DELETE FROM events WHERE idEv=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idEv);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur suppression evenement: " + ex.getMessage(), ex);
        }
    }

    public List<Event> getAll() {
        String sql = "SELECT idEv, titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant "
                + "FROM events ORDER BY startDateEv DESC";
        List<Event> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur lecture evenements: " + ex.getMessage(), ex);
        }
        return list;
    }

    public Event getById(int idEv) {
        String sql = "SELECT idEv, titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant "
                + "FROM events WHERE idEv=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idEv);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur lecture evenement: " + ex.getMessage(), ex);
        }
        return null;
    }

    private Event map(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setIdEv(rs.getInt("idEv"));
        e.setTitleEv(rs.getString("titleEv"));
        e.setDescriptionEv(rs.getString("descriptionEv"));
        Timestamp start = rs.getTimestamp("startDateEv");
        Timestamp end = rs.getTimestamp("endDateEv");
        e.setStartDateEv(start == null ? null : start.toLocalDateTime());
        e.setEndDateEv(end == null ? null : end.toLocalDateTime());
        e.setOrganisateurName(rs.getString("organisateurName"));
        e.setCapaciteEvnt(rs.getInt("capaciteEvnt"));
        e.setLocalisationEv(rs.getString("localisationEv"));
        int idGerant = rs.getInt("idGerant");
        e.setIdGerant(rs.wasNull() ? null : idGerant);
        return e;
    }

    private void validate(Event e, boolean create) {
        if (e == null) {
            throw new IllegalArgumentException("Evenement obligatoire.");
        }
        if (!create && e.getIdEv() <= 0) {
            throw new IllegalArgumentException("idEv invalide.");
        }
        if (e.getTitleEv() == null || e.getTitleEv().trim().isEmpty()) {
            throw new IllegalArgumentException("Titre obligatoire.");
        }
        if (e.getStartDateEv() == null || e.getEndDateEv() == null) {
            throw new IllegalArgumentException("Dates obligatoires.");
        }
        if (!e.getStartDateEv().isBefore(e.getEndDateEv())) {
            throw new IllegalArgumentException("La date de debut doit etre avant la date de fin.");
        }
        if (e.getCapaciteEvnt() <= 0) {
            throw new IllegalArgumentException("Capacite > 0 obligatoire.");
        }
    }
}
