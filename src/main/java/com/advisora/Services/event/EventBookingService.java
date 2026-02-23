package com.advisora.Services.event;

import com.advisora.Model.event.EventBooking;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class EventBookingService {

    public void createBooking(int clientId, int eventId, int seats) {
        if (clientId <= 0) {
            throw new IllegalArgumentException("Client invalide.");
        }
        if (eventId <= 0) {
            throw new IllegalArgumentException("Evenement invalide.");
        }
        if (seats <= 0) {
            throw new IllegalArgumentException("Nombre de places > 0 obligatoire.");
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                EventInfo info = lockEventInfo(cnx, eventId);
                LocalDateTime now = LocalDateTime.now();
                if (info.startDate != null && now.isAfter(info.startDate)) {
                    throw new IllegalArgumentException("Les reservations sont fermees pour cet evenement.");
                }
                int reserved = getReservedSeatsForUpdate(cnx, eventId);
                int available = Math.max(0, info.capacity - reserved);
                if (seats > available) {
                    throw new IllegalArgumentException("Places insuffisantes. Disponible: " + available);
                }

                String insertSql = "INSERT INTO bookings (bookingDate, numTicketBk, totalPrixBk, idEv, idUser) VALUES (NOW(), ?, ?, ?, ?)";
                try (PreparedStatement ps = cnx.prepareStatement(insertSql)) {
                    ps.setInt(1, seats);
                    ps.setDouble(2, 0.0);
                    ps.setInt(3, eventId);
                    ps.setInt(4, clientId);
                    ps.executeUpdate();
                }

                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur reservation evenement: " + ex.getMessage(), ex);
        }
    }

    public void cancelBookingForClient(int clientId, int bookingId) {
        if (clientId <= 0 || bookingId <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        String sql = "DELETE FROM bookings WHERE idBk=? AND idUser=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, clientId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("Reservation introuvable.");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur annulation reservation: " + ex.getMessage(), ex);
        }
    }

    public void cancelBookingAsManager(int bookingId) {
        if (bookingId <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        String sql = "DELETE FROM bookings WHERE idBk=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new IllegalArgumentException("Reservation introuvable.");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur annulation reservation: " + ex.getMessage(), ex);
        }
    }

    public List<EventBooking> listByClient(int clientId) {
        String sql = "SELECT b.idBk, b.bookingDate, b.numTicketBk, b.totalPrixBk, b.idEv, e.titleEv, e.startDateEv, e.endDateEv, b.idUser, "
                + "CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS clientName "
                + "FROM bookings b "
                + "JOIN events e ON e.idEv = b.idEv "
                + "LEFT JOIN `user` u ON u.idUser = b.idUser "
                + "WHERE b.idUser = ? "
                + "ORDER BY b.bookingDate DESC";
        return queryBookings(sql, ps -> ps.setInt(1, clientId));
    }

    public List<EventBooking> listAll() {
        String sql = "SELECT b.idBk, b.bookingDate, b.numTicketBk, b.totalPrixBk, b.idEv, e.titleEv, e.startDateEv, e.endDateEv, b.idUser, "
                + "CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS clientName "
                + "FROM bookings b "
                + "JOIN events e ON e.idEv = b.idEv "
                + "LEFT JOIN `user` u ON u.idUser = b.idUser "
                + "ORDER BY b.bookingDate DESC";
        return queryBookings(sql, null);
    }

    public Map<Integer, Integer> getReservedSeatsByEventIds(List<Integer> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> ids = eventIds.stream().filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        StringJoiner placeholders = new StringJoiner(",");
        for (int i = 0; i < ids.size(); i++) {
            placeholders.add("?");
        }

        String sql = "SELECT idEv, COALESCE(SUM(numTicketBk),0) AS reserved "
                + "FROM bookings WHERE idEv IN (" + placeholders + ") GROUP BY idEv";
        Map<Integer, Integer> reserved = new HashMap<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reserved.put(rs.getInt("idEv"), rs.getInt("reserved"));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur calcul places reservees: " + ex.getMessage(), ex);
        }
        for (Integer id : ids) {
            reserved.putIfAbsent(id, 0);
        }
        return reserved;
    }

    private List<EventBooking> queryBookings(String sql, PreparedStatementConfigurer configurer) {
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            if (configurer != null) {
                configurer.configure(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<EventBooking> list = new ArrayList<>();
                while (rs.next()) {
                    EventBooking b = new EventBooking();
                    b.setIdBk(rs.getInt("idBk"));
                    Timestamp ts = rs.getTimestamp("bookingDate");
                    b.setBookingDate(ts == null ? null : ts.toLocalDateTime());
                    b.setNumTicketBk(rs.getInt("numTicketBk"));
                    b.setTotalPrixBk(rs.getDouble("totalPrixBk"));
                    b.setIdEv(rs.getInt("idEv"));
                    b.setEventTitle(rs.getString("titleEv"));
                    Timestamp start = rs.getTimestamp("startDateEv");
                    b.setEventStart(start == null ? null : start.toLocalDateTime());
                    Timestamp end = rs.getTimestamp("endDateEv");
                    b.setEventEnd(end == null ? null : end.toLocalDateTime());
                    b.setIdUser(rs.getInt("idUser"));
                    b.setClientName(rs.getString("clientName"));
                    list.add(b);
                }
                return list;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur lecture reservations: " + ex.getMessage(), ex);
        }
    }

    private EventInfo lockEventInfo(Connection cnx, int eventId) throws SQLException {
        String sql = "SELECT capaciteEvnt, startDateEv FROM events WHERE idEv = ? FOR UPDATE";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Evenement introuvable.");
                }
                int cap = rs.getInt("capaciteEvnt");
                Timestamp start = rs.getTimestamp("startDateEv");
                EventInfo info = new EventInfo();
                info.capacity = cap;
                info.startDate = start == null ? null : start.toLocalDateTime();
                return info;
            }
        }
    }

    private int getReservedSeatsForUpdate(Connection cnx, int eventId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(numTicketBk),0) FROM bookings WHERE idEv = ? FOR UPDATE";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static class EventInfo {
        int capacity;
        LocalDateTime startDate;
    }

    @FunctionalInterface
    private interface PreparedStatementConfigurer {
        void configure(PreparedStatement ps) throws SQLException;
    }
}
