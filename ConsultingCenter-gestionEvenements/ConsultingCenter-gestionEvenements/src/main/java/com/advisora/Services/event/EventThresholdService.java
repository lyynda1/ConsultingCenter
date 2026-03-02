package com.advisora.Services.event;

import com.advisora.Model.strategie.Notification;
import com.advisora.Services.strategie.NotificationManager;
import com.advisora.enums.UserRole;
import com.advisora.utils.EmailSender;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EventThresholdService {

    public int processDueThresholdCancellations() {
        List<ThresholdCandidate> candidates = findDueCandidates();
        int cancelledCount = 0;

        for (ThresholdCandidate candidate : candidates) {
            if (candidate.capacity <= 0 || candidate.thresholdPercent == null || candidate.thresholdPercent <= 0) {
                continue;
            }
            double bookingRate = ((double) candidate.reservedSeats / (double) candidate.capacity) * 100.0;
            if (bookingRate >= candidate.thresholdPercent) {
                continue;
            }

            cancelEventAndBookings(candidate.eventId);
            notifyCancellation(candidate, bookingRate);
            cancelledCount++;
        }

        return cancelledCount;
    }

    private List<ThresholdCandidate> findDueCandidates() {
        String sql = "SELECT e.idEv, e.titleEv, e.capaciteEvnt, e.minReservationThreshold, "
                + "COALESCE(SUM(CASE WHEN COALESCE(b.bookingStatus,'CONFIRMED') IN ('CONFIRMED','PENDING_PAYMENT') THEN b.numTicketBk ELSE 0 END),0) AS reservedSeats "
                + "FROM events e "
                + "LEFT JOIN bookings b ON b.idEv = e.idEv "
                + "WHERE e.thresholdDeadline IS NOT NULL "
                + "AND e.thresholdDeadline <= NOW() "
                + "AND COALESCE(e.statusEv,'PUBLISHED') = 'PUBLISHED' "
                + "GROUP BY e.idEv, e.titleEv, e.capaciteEvnt, e.minReservationThreshold";

        List<ThresholdCandidate> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ThresholdCandidate c = new ThresholdCandidate();
                c.eventId = rs.getInt("idEv");
                c.title = rs.getString("titleEv");
                c.capacity = rs.getInt("capaciteEvnt");
                double threshold = rs.getDouble("minReservationThreshold");
                c.thresholdPercent = rs.wasNull() ? null : threshold;
                c.reservedSeats = rs.getInt("reservedSeats");
                list.add(c);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur scan seuil reservations: " + ex.getMessage(), ex);
        }
        return list;
    }

    private void cancelEventAndBookings(int eventId) {
        String cancelEventSql = "UPDATE events SET statusEv='CANCELLED' WHERE idEv=?";
        String cancelBookingsSql = "UPDATE bookings "
                + "SET bookingStatus='CANCELLED', cancelReasonBk='Cancelled: reservation threshold not met', "
                + "refundAmountBk=CASE WHEN totalPrixBk > 0 THEN totalPrixBk ELSE refundAmountBk END, "
                + "refundDateBk=CASE WHEN totalPrixBk > 0 THEN NOW() ELSE refundDateBk END "
                + "WHERE idEv=? AND COALESCE(bookingStatus,'CONFIRMED') IN ('CONFIRMED','PENDING_PAYMENT')";

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean autoCommit = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try (PreparedStatement psEvent = cnx.prepareStatement(cancelEventSql);
                 PreparedStatement psBookings = cnx.prepareStatement(cancelBookingsSql)) {
                psEvent.setInt(1, eventId);
                psEvent.executeUpdate();

                psBookings.setInt(1, eventId);
                psBookings.executeUpdate();

                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur annulation evenement seuil: " + ex.getMessage(), ex);
        }
    }

    private void notifyCancellation(ThresholdCandidate candidate, double bookingRate) {
        String title = "Evenement annule";
        String message = "L'evenement '" + candidate.title + "' est annule car le seuil minimal de reservation n'a pas ete atteint ("
                + round2(bookingRate) + "% / " + round2(candidate.thresholdPercent) + "%).";

        try {
            NotificationManager.getInstance().createIfNotExists(title, message, UserRole.CLIENT);
            NotificationManager.getInstance().addNotification(new Notification(title, message));
        } catch (Exception ex) {
            System.err.println("[EVENT-THRESHOLD] notification in-app skipped: " + ex.getMessage());
        }

        List<String> recipients = findAttendeeEmails(candidate.eventId);
        if (recipients.isEmpty()) {
            return;
        }

        EmailSender sender = createEmailSender();
        if (sender == null) {
            return;
        }

        for (String recipient : recipients) {
            try {
                sender.send(recipient, title, message + "\n\nSi vous avez paye une reservation, un remboursement est en cours.");
            } catch (Exception ex) {
                System.err.println("[EVENT-THRESHOLD] email failed for " + recipient + ": " + ex.getMessage());
            }
        }
    }

    private List<String> findAttendeeEmails(int eventId) {
        String sql = "SELECT DISTINCT u.EmailUser "
                + "FROM bookings b "
                + "JOIN `user` u ON u.idUser = b.idUser "
                + "WHERE b.idEv = ? AND u.EmailUser IS NOT NULL AND u.EmailUser <> ''";
        List<String> emails = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    emails.add(rs.getString("EmailUser"));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur lecture emails participants: " + ex.getMessage(), ex);
        }
        return emails;
    }

    private EmailSender createEmailSender() {
        String host = getenvTrim("ADVISORA_SMTP_HOST", "smtp.gmail.com");
        String portText = getenvTrim("ADVISORA_SMTP_PORT", "587");
        String user = getenvTrim("ADVISORA_SMTP_USER", null);
        String password = getenvTrim("ADVISORA_SMTP_PASSWORD", null);

        if (user == null || password == null) {
            return null;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (Exception ex) {
            port = 587;
        }
        return new EmailSender(host, port, user, password);
    }

    private String getenvTrim(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class ThresholdCandidate {
        int eventId;
        String title;
        int capacity;
        Double thresholdPercent;
        int reservedSeats;
    }
}
