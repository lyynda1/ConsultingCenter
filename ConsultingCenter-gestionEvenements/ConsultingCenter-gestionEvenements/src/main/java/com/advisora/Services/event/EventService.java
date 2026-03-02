package com.advisora.Services.event;

import com.advisora.Model.event.Event;
import com.advisora.enums.EventStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class EventService {

    public void add(Event e) {
        validate(e, true);
        String sql = "INSERT INTO events (titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant, ticketPrice, currencyCode, minReservationThreshold, thresholdDeadline, statusEv, categoryEv, imageUrlEv) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setDouble(9, e.getTicketPrice());
            ps.setString(10, normalizeCurrency(e.getCurrencyCode()));
            if (e.getMinReservationThreshold() == null) {
                ps.setNull(11, java.sql.Types.DECIMAL);
            } else {
                ps.setDouble(11, e.getMinReservationThreshold());
            }
            if (e.getThresholdDeadline() == null) {
                ps.setNull(12, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(12, Timestamp.valueOf(e.getThresholdDeadline()));
            }
            ps.setString(13, normalizeStatus(e.getStatusEv()).name());
            ps.setString(14, trimToNull(e.getCategoryEv()));
            ps.setString(15, trimToNull(e.getImageUrlEv()));
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
        String sql = "UPDATE events SET titleEv=?, descriptionEv=?, startDateEv=?, endDateEv=?, organisateurName=?, capaciteEvnt=?, localisationEv=?, idGerant=?, ticketPrice=?, currencyCode=?, minReservationThreshold=?, thresholdDeadline=?, statusEv=?, categoryEv=?, imageUrlEv=? WHERE idEv=?";
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
            ps.setDouble(9, e.getTicketPrice());
            ps.setString(10, normalizeCurrency(e.getCurrencyCode()));
            if (e.getMinReservationThreshold() == null) {
                ps.setNull(11, java.sql.Types.DECIMAL);
            } else {
                ps.setDouble(11, e.getMinReservationThreshold());
            }
            if (e.getThresholdDeadline() == null) {
                ps.setNull(12, java.sql.Types.TIMESTAMP);
            } else {
                ps.setTimestamp(12, Timestamp.valueOf(e.getThresholdDeadline()));
            }
            ps.setString(13, normalizeStatus(e.getStatusEv()).name());
            ps.setString(14, trimToNull(e.getCategoryEv()));
            ps.setString(15, trimToNull(e.getImageUrlEv()));
            ps.setInt(16, e.getIdEv());
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
        String sql = "SELECT idEv, titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant, ticketPrice, currencyCode, minReservationThreshold, thresholdDeadline, statusEv, categoryEv, imageUrlEv "
                + "FROM events ORDER BY startDateEv DESC";
        List<Event> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException ex) {
            // Fallback legacy schema without new columns
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown column")) {
                String legacy = "SELECT idEv, titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant "
                        + "FROM events ORDER BY startDateEv DESC";
                try (Connection cnx = MyConnection.getInstance().getConnection();
                     PreparedStatement ps = cnx.prepareStatement(legacy);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapLegacy(rs));
                    }
                } catch (SQLException ex2) {
                    throw new RuntimeException("Erreur lecture evenements (schema legacy): " + ex2.getMessage(), ex2);
                }
            } else {
                throw new RuntimeException("Erreur lecture evenements: " + ex.getMessage(), ex);
            }
        }
        return list;
    }

    public Event getById(int idEv) {
        String sql = "SELECT idEv, titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant, ticketPrice, currencyCode, minReservationThreshold, thresholdDeadline, statusEv, categoryEv, imageUrlEv "
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
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown column")) {
                String legacy = "SELECT idEv, titleEv, descriptionEv, startDateEv, endDateEv, organisateurName, capaciteEvnt, localisationEv, idGerant "
                        + "FROM events WHERE idEv=?";
                try (Connection cnx = MyConnection.getInstance().getConnection();
                     PreparedStatement ps = cnx.prepareStatement(legacy)) {
                    ps.setInt(1, idEv);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return mapLegacy(rs);
                        }
                    }
                } catch (SQLException ex2) {
                    throw new RuntimeException("Erreur lecture evenement (schema legacy): " + ex2.getMessage(), ex2);
                }
            } else {
                throw new RuntimeException("Erreur lecture evenement: " + ex.getMessage(), ex);
            }
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
        e.setTicketPrice(rs.getDouble("ticketPrice"));
        e.setCurrencyCode(normalizeCurrency(rs.getString("currencyCode")));
        double threshold = rs.getDouble("minReservationThreshold");
        e.setMinReservationThreshold(rs.wasNull() ? null : threshold);
        Timestamp thresholdDeadline = rs.getTimestamp("thresholdDeadline");
        e.setThresholdDeadline(thresholdDeadline == null ? null : thresholdDeadline.toLocalDateTime());
        e.setStatusEv(parseStatus(rs.getString("statusEv")));
        e.setCategoryEv(rs.getString("categoryEv"));
        e.setImageUrlEv(rs.getString("imageUrlEv"));
        return e;
    }

    private Event mapLegacy(ResultSet rs) throws SQLException {
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
        e.setTicketPrice(0.0);
        e.setCurrencyCode("TND");
        e.setMinReservationThreshold(null);
        e.setThresholdDeadline(null);
        e.setStatusEv(EventStatus.PUBLISHED);
        e.setCategoryEv(null);
        e.setImageUrlEv(null);
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
        if (e.getTicketPrice() < 0) {
            throw new IllegalArgumentException("Prix ticket invalide.");
        }
        if (e.getMinReservationThreshold() != null) {
            if (e.getMinReservationThreshold() < 0 || e.getMinReservationThreshold() > 100) {
                throw new IllegalArgumentException("Seuil de reservation doit etre entre 0 et 100.");
            }
            if (e.getThresholdDeadline() == null) {
                throw new IllegalArgumentException("Date limite du seuil obligatoire quand un seuil est defini.");
            }
        }
        if (e.getThresholdDeadline() != null && !e.getThresholdDeadline().isBefore(e.getStartDateEv())) {
            throw new IllegalArgumentException("La date limite du seuil doit etre avant le debut de l'evenement.");
        }
        e.setCurrencyCode(normalizeCurrency(e.getCurrencyCode()));
        e.setStatusEv(normalizeStatus(e.getStatusEv()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return "TND";
        }
        return currency.trim().toUpperCase();
    }

    private EventStatus normalizeStatus(EventStatus status) {
        return status == null ? EventStatus.PUBLISHED : status;
    }

    private EventStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return EventStatus.PUBLISHED;
        }
        try {
            return EventStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception ignored) {
            return EventStatus.PUBLISHED;
        }
    }
}
