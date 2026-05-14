package com.advisora.Services.event;

import com.advisora.Model.event.EventBooking;
import com.advisora.enums.BookingStatus;
import com.advisora.enums.EventStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private final EventQrTicketService qrTicketService = new EventQrTicketService();
    private final Map<String, Boolean> columnCache = new HashMap<>();

    public void createBooking(int clientId, int eventId, int seats) {
        createBookingAndReturnId(clientId, eventId, seats);
    }

    public int createBookingAndReturnId(int clientId, int eventId, int seats) {
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
                if (info.status == EventStatus.CANCELLED || info.status == EventStatus.COMPLETED) {
                    throw new IllegalArgumentException("Reservation impossible pour cet evenement.");
                }
                int reserved = getReservedSeatsForUpdate(cnx, eventId);
                int available = Math.max(0, info.capacity - reserved);
                if (seats > available) {
                    throw new IllegalArgumentException("Places insuffisantes. Disponible: " + available);
                }

                double totalPrice = round2(info.ticketPrice * seats);
                BookingStatus status = totalPrice > 0 ? BookingStatus.PENDING_PAYMENT : BookingStatus.CONFIRMED;

                String statusCol = resolveBookingStatusColumn(cnx);
                boolean hasPaymentReference = hasColumn(cnx, "bookings", "paymentReference");
                boolean hasNotificationSent = hasColumn(cnx, "bookings", "notificationSentBk");
                StringBuilder columns = new StringBuilder("bookingDate, numTicketBk, totalPrixBk, idEv, idUser");
                StringBuilder values = new StringBuilder("NOW(), ?, ?, ?, ?");
                if (statusCol != null) {
                    columns.append(", ").append(statusCol);
                    values.append(", ?");
                }
                if (hasPaymentReference) {
                    columns.append(", paymentReference");
                    values.append(", NULL");
                }
                if (hasNotificationSent) {
                    columns.append(", notificationSentBk");
                    values.append(", FALSE");
                }
                String insertSql = "INSERT INTO bookings (" + columns + ") VALUES (" + values + ")";
                int bookingId;
                try (PreparedStatement ps = cnx.prepareStatement(
                        insertSql,
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, seats);
                    ps.setDouble(2, totalPrice);
                    ps.setInt(3, eventId);
                    ps.setInt(4, clientId);
                    if (statusCol != null) {
                        ps.setString(5, status.name());
                    }
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new IllegalStateException("Creation reservation: id non genere.");
                        }
                        bookingId = keys.getInt(1);
                    }
                }

                cnx.commit();
                return bookingId;
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

    public EventBooking getById(int bookingId) {
        if (bookingId <= 0) {
            return null;
        }
        List<EventBooking> list = queryBookings(
                cnx -> buildBookingSelect(cnx, false, null) + " WHERE b.idBk = ?",
                ps -> ps.setInt(1, bookingId)
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    public void updatePaymentReference(int bookingId, String paymentReference) {
        if (bookingId <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            if (!hasColumn(cnx, "bookings", "paymentReference")) {
                return;
            }
            String sql = "UPDATE bookings SET paymentReference=? WHERE idBk=?";
            try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            ps.setString(1, paymentReference);
            ps.setInt(2, bookingId);
            ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur mise a jour reference paiement: " + ex.getMessage(), ex);
        }
    }

    public void markNotificationSent(int bookingId) {
        if (bookingId <= 0) {
            return;
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            if (!hasColumn(cnx, "bookings", "notificationSentBk")) {
                return;
            }
            String sql = "UPDATE bookings SET notificationSentBk=TRUE WHERE idBk=?";
            try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur update notification booking: " + ex.getMessage(), ex);
        }
    }

    public List<EventBooking> findBookingsForReminder(int hoursBefore) {
        if (hoursBefore <= 0) {
            return Collections.emptyList();
        }

        String reminderColumn = hoursBefore == 48 ? "reminder48SentBk" : "reminder24SentBk";
        if (!hasReminderColumn(reminderColumn)) {
            return Collections.emptyList();
        }

        int lowerBound = Math.max(0, hoursBefore - 24);
        return queryBookings(
                cnx -> {
                    String statusExpr = bookingStatusExpr(cnx, "b");
                    return buildBookingSelect(cnx, true, reminderColumn)
                            + " WHERE " + statusExpr + "='CONFIRMED' "
                            + "AND e.startDateEv > NOW() "
                            + "AND e.startDateEv <= DATE_ADD(NOW(), INTERVAL ? HOUR) "
                            + "AND e.startDateEv > DATE_ADD(NOW(), INTERVAL ? HOUR) "
                            + "AND COALESCE(b." + reminderColumn + ", FALSE)=FALSE "
                            + "ORDER BY e.startDateEv ASC";
                },
                ps -> {
                    ps.setInt(1, hoursBefore);
                    ps.setInt(2, lowerBound);
                }
        );
    }

    public void markReminderSent(int bookingId, int hoursBefore) {
        if (bookingId <= 0 || hoursBefore <= 0) {
            return;
        }
        String reminderColumn = hoursBefore == 48 ? "reminder48SentBk" : "reminder24SentBk";
        if (!hasReminderColumn(reminderColumn)) {
            return;
        }
        String sql = "UPDATE bookings SET " + reminderColumn + "=TRUE WHERE idBk=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur update reminder booking: " + ex.getMessage(), ex);
        }
    }

    public void ensureQrTicket(int bookingId) {
        EventBooking booking = getById(bookingId);
        if (booking == null) {
            throw new IllegalArgumentException("Reservation introuvable.");
        }
        if (booking.getQrTokenBk() != null && !booking.getQrTokenBk().isBlank()) {
            return;
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean hasQrToken = hasColumn(cnx, "bookings", "qrTokenBk");
            boolean hasQrImage = hasColumn(cnx, "bookings", "qrImagePathBk");
            if (!hasQrToken && !hasQrImage) {
                return;
            }
            EventQrTicketService.QrTicket qrTicket = qrTicketService.createTicket(booking.getIdBk(), booking.getIdEv(), booking.getIdUser());
            StringBuilder sql = new StringBuilder("UPDATE bookings SET ");
            if (hasQrToken) {
                sql.append("qrTokenBk=?");
            }
            if (hasQrImage) {
                if (hasQrToken) {
                    sql.append(", ");
                }
                sql.append("qrImagePathBk=?");
            }
            sql.append(" WHERE idBk=?");
            try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
                int idx = 1;
                if (hasQrToken) {
                    ps.setString(idx++, qrTicket.getToken());
                }
                if (hasQrImage) {
                    ps.setString(idx++, qrTicket.getImagePath());
                }
                ps.setInt(idx, bookingId);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur generation ticket QR: " + ex.getMessage(), ex);
        }
    }

    public void cancelBookingForClient(int clientId, int bookingId) {
        if (clientId <= 0 || bookingId <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            String statusCol = resolveBookingStatusColumn(cnx);
            boolean hasCancelReason = hasColumn(cnx, "bookings", "cancelReasonBk");
            boolean hasRefundAmount = hasColumn(cnx, "bookings", "refundAmountBk");
            boolean hasRefundDate = hasColumn(cnx, "bookings", "refundDateBk");
            if (statusCol == null && !hasCancelReason && !hasRefundAmount && !hasRefundDate) {
                throw new IllegalStateException("Aucune colonne pour annuler la reservation.");
            }
            String statusExpr = bookingStatusExpr(cnx, "");
            StringBuilder sql = new StringBuilder("UPDATE bookings SET ");
            boolean hasAny = false;
            if (statusCol != null) {
                sql.append(statusCol).append("=?");
                hasAny = true;
            }
            if (hasCancelReason) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("cancelReasonBk=?");
                hasAny = true;
            }
            if (hasRefundAmount) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("refundAmountBk=CASE WHEN totalPrixBk > 0 THEN totalPrixBk ELSE refundAmountBk END");
                hasAny = true;
            }
            if (hasRefundDate) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("refundDateBk=CASE WHEN totalPrixBk > 0 THEN NOW() ELSE refundDateBk END");
            }
            sql.append(" WHERE idBk=? AND idUser=? AND ").append(statusExpr).append(" NOT IN ('CANCELLED','REFUNDED')");
            try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
                int idx = 1;
                if (statusCol != null) {
                    ps.setString(idx++, BookingStatus.CANCELLED.name());
                }
                if (hasCancelReason) {
                    ps.setString(idx++, "Cancelled by attendee");
                }
                ps.setInt(idx++, bookingId);
                ps.setInt(idx, clientId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new IllegalArgumentException("Reservation introuvable.");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur annulation reservation: " + ex.getMessage(), ex);
        }
    }

    public void cancelBookingAsManager(int bookingId) {
        if (bookingId <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            String statusCol = resolveBookingStatusColumn(cnx);
            boolean hasCancelReason = hasColumn(cnx, "bookings", "cancelReasonBk");
            boolean hasRefundAmount = hasColumn(cnx, "bookings", "refundAmountBk");
            boolean hasRefundDate = hasColumn(cnx, "bookings", "refundDateBk");
            if (statusCol == null && !hasCancelReason && !hasRefundAmount && !hasRefundDate) {
                throw new IllegalStateException("Aucune colonne pour annuler la reservation.");
            }
            String statusExpr = bookingStatusExpr(cnx, "");
            StringBuilder sql = new StringBuilder("UPDATE bookings SET ");
            boolean hasAny = false;
            if (statusCol != null) {
                sql.append(statusCol).append("=?");
                hasAny = true;
            }
            if (hasCancelReason) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("cancelReasonBk=?");
                hasAny = true;
            }
            if (hasRefundAmount) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("refundAmountBk=CASE WHEN totalPrixBk > 0 THEN totalPrixBk ELSE refundAmountBk END");
                hasAny = true;
            }
            if (hasRefundDate) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("refundDateBk=CASE WHEN totalPrixBk > 0 THEN NOW() ELSE refundDateBk END");
            }
            sql.append(" WHERE idBk=? AND ").append(statusExpr).append(" NOT IN ('CANCELLED','REFUNDED')");
            try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
                int idx = 1;
                if (statusCol != null) {
                    ps.setString(idx++, BookingStatus.CANCELLED.name());
                }
                if (hasCancelReason) {
                    ps.setString(idx++, "Cancelled by manager");
                }
                ps.setInt(idx, bookingId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new IllegalArgumentException("Reservation introuvable.");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur annulation reservation: " + ex.getMessage(), ex);
        }
    }

    public List<EventBooking> listByClient(int clientId) {
        return queryBookings(
                cnx -> buildBookingSelect(cnx, false, null) + " WHERE b.idUser = ? ORDER BY b.bookingDate DESC",
                ps -> ps.setInt(1, clientId)
        );
    }

    public List<EventBooking> listAll() {
        return queryBookings(
                cnx -> buildBookingSelect(cnx, false, null) + " ORDER BY b.bookingDate DESC",
                null
        );
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

        Map<Integer, Integer> reserved = new HashMap<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(
                     "SELECT idEv, COALESCE(SUM(numTicketBk),0) AS reserved "
                             + "FROM bookings WHERE idEv IN (" + placeholders + ") "
                             + "AND " + bookingStatusExpr(cnx, "") + " IN ('CONFIRMED','PENDING_PAYMENT') GROUP BY idEv"
             )) {
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

    private List<EventBooking> queryBookings(SqlBuilder sqlBuilder, PreparedStatementConfigurer configurer) {
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sqlBuilder.build(cnx))) {
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
                    b.setBookingStatus(parseBookingStatus(rs.getString("bookingStatus")));
                    b.setPaymentReference(rs.getString("paymentReference"));
                    double refundAmount = rs.getDouble("refundAmountBk");
                    b.setRefundAmountBk(rs.wasNull() ? null : refundAmount);
                    Timestamp refundDate = rs.getTimestamp("refundDateBk");
                    b.setRefundDateBk(refundDate == null ? null : refundDate.toLocalDateTime());
                    b.setCancelReasonBk(rs.getString("cancelReasonBk"));
                    b.setNotificationSentBk(rs.getBoolean("notificationSentBk"));
                    b.setClientEmail(rs.getString("clientEmail"));
                    b.setEventCurrencyCode(rs.getString("currencyCode"));
                    b.setQrTokenBk(rs.getString("qrTokenBk"));
                    b.setQrImagePathBk(rs.getString("qrImagePathBk"));
                    list.add(b);
                }
                return list;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur lecture reservations: " + ex.getMessage(), ex);
        }
    }

    private String buildBookingSelect(Connection cnx, boolean includeReminder, String reminderColumn) throws SQLException {
        String currencyExpr = hasColumn(cnx, "events", "currencyCode") ? "e.currencyCode" : "'TND'";
        StringBuilder sql = new StringBuilder();
        String statusSelect = bookingStatusSelect(cnx, "b");
        String paymentRefSelect = bookingColumnSelect(cnx, "b", "paymentReference", "NULL");
        String refundAmountSelect = bookingColumnSelect(cnx, "b", "refundAmountBk", "NULL");
        String refundDateSelect = bookingColumnSelect(cnx, "b", "refundDateBk", "NULL");
        String cancelReasonSelect = bookingColumnSelect(cnx, "b", "cancelReasonBk", "NULL");
        String notificationSentSelect = bookingColumnSelect(cnx, "b", "notificationSentBk", "FALSE");
        String qrTokenSelect = bookingColumnSelect(cnx, "b", "qrTokenBk", "NULL");
        String qrImagePathSelect = bookingColumnSelect(cnx, "b", "qrImagePathBk", "NULL");
        sql.append("SELECT b.idBk, b.bookingDate, b.numTicketBk, b.totalPrixBk, b.idEv, ")
                .append("e.titleEv, e.startDateEv, e.endDateEv, ")
                .append(currencyExpr).append(" AS currencyCode, ")
                .append("b.idUser, ").append(statusSelect).append(", ")
                .append(paymentRefSelect).append(", ")
                .append(refundAmountSelect).append(", ")
                .append(refundDateSelect).append(", ")
                .append(cancelReasonSelect).append(", ")
                .append(notificationSentSelect).append(", ")
                .append(qrTokenSelect).append(", ")
                .append(qrImagePathSelect);
        if (includeReminder && reminderColumn != null) {
            sql.append(", b.").append(reminderColumn);
        }
        sql.append(", u.EmailUser AS clientEmail, ")
                .append("CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS clientName ")
                .append("FROM bookings b ")
                .append("JOIN events e ON e.idEv = b.idEv ")
                .append("LEFT JOIN `user` u ON u.idUser = b.idUser");
        return sql.toString();
    }

    private EventInfo lockEventInfo(Connection cnx, int eventId) throws SQLException {
        String sql = "SELECT capaciteEvnt, startDateEv, ticketPrice, currencyCode, statusEv FROM events WHERE idEv = ? FOR UPDATE";
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
                info.ticketPrice = rs.getDouble("ticketPrice");
                info.currencyCode = rs.getString("currencyCode");
                info.status = parseEventStatus(rs.getString("statusEv"));
                return info;
            }
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unknown column")) {
                String legacy = "SELECT capaciteEvnt, startDateEv FROM events WHERE idEv = ? FOR UPDATE";
                try (PreparedStatement ps2 = cnx.prepareStatement(legacy)) {
                    ps2.setInt(1, eventId);
                    try (ResultSet rs = ps2.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Evenement introuvable.");
                        }
                        int cap = rs.getInt("capaciteEvnt");
                        Timestamp start = rs.getTimestamp("startDateEv");
                        EventInfo info = new EventInfo();
                        info.capacity = cap;
                        info.startDate = start == null ? null : start.toLocalDateTime();
                        info.ticketPrice = 0.0;
                        info.currencyCode = "TND";
                        info.status = EventStatus.PUBLISHED;
                        return info;
                    }
                }
            }
            throw ex;
        }
    }

    private int getReservedSeatsForUpdate(Connection cnx, int eventId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(numTicketBk),0) FROM bookings WHERE idEv = ? AND "
                + bookingStatusExpr(cnx, "") + " IN ('CONFIRMED','PENDING_PAYMENT') FOR UPDATE";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void markBookingPaid(int bookingId, String paymentReference) {
        if (bookingId <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            String statusCol = resolveBookingStatusColumn(cnx);
            boolean hasPaymentReference = hasColumn(cnx, "bookings", "paymentReference");
            if (statusCol == null && !hasPaymentReference) {
                throw new IllegalStateException("Aucune colonne pour confirmer le paiement.");
            }
            StringBuilder sql = new StringBuilder("UPDATE bookings SET ");
            if (statusCol != null) {
                sql.append(statusCol).append("=?");
            }
            if (hasPaymentReference) {
                if (statusCol != null) {
                    sql.append(", ");
                }
                sql.append("paymentReference=?");
            }
            sql.append(" WHERE idBk=?");
            try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
                int idx = 1;
                if (statusCol != null) {
                    ps.setString(idx++, BookingStatus.CONFIRMED.name());
                }
                if (hasPaymentReference) {
                    ps.setString(idx++, paymentReference);
                }
                ps.setInt(idx, bookingId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new IllegalArgumentException("Reservation introuvable.");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur confirmation paiement reservation: " + ex.getMessage(), ex);
        }
        ensureQrTicket(bookingId);
    }

    public void markBookingRefunded(int bookingId, String reason) {
        if (bookingId <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            String statusCol = resolveBookingStatusColumn(cnx);
            boolean hasCancelReason = hasColumn(cnx, "bookings", "cancelReasonBk");
            boolean hasRefundAmount = hasColumn(cnx, "bookings", "refundAmountBk");
            boolean hasRefundDate = hasColumn(cnx, "bookings", "refundDateBk");
            if (statusCol == null && !hasCancelReason && !hasRefundAmount && !hasRefundDate) {
                throw new IllegalStateException("Aucune colonne pour marquer le remboursement.");
            }
            StringBuilder sql = new StringBuilder("UPDATE bookings SET ");
            boolean hasAny = false;
            if (statusCol != null) {
                sql.append(statusCol).append("=?");
                hasAny = true;
            }
            if (hasCancelReason) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("cancelReasonBk=?");
                hasAny = true;
            }
            if (hasRefundAmount) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("refundAmountBk=totalPrixBk");
                hasAny = true;
            }
            if (hasRefundDate) {
                if (hasAny) {
                    sql.append(", ");
                }
                sql.append("refundDateBk=NOW()");
            }
            sql.append(" WHERE idBk=?");
            try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
                int idx = 1;
                if (statusCol != null) {
                    ps.setString(idx++, BookingStatus.REFUNDED.name());
                }
                if (hasCancelReason) {
                    ps.setString(idx++, reason == null || reason.isBlank() ? "Refund processed" : reason.trim());
                }
                ps.setInt(idx, bookingId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new IllegalArgumentException("Reservation introuvable.");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur remboursement reservation: " + ex.getMessage(), ex);
        }
    }

    private String bookingStatusSelect(Connection cnx, String alias) throws SQLException {
        String col = resolveBookingStatusColumn(cnx);
        if (col == null) {
            return "'CONFIRMED' AS bookingStatus";
        }
        return alias + "." + col + " AS bookingStatus";
    }

    private String bookingColumnSelect(Connection cnx, String alias, String column, String defaultExpr) throws SQLException {
        if (!hasColumn(cnx, "bookings", column)) {
            return defaultExpr + " AS " + column;
        }
        return alias + "." + column + " AS " + column;
    }

    private String bookingStatusExpr(Connection cnx, String alias) throws SQLException {
        String col = resolveBookingStatusColumn(cnx);
        if (col == null) {
            return "'CONFIRMED'";
        }
        String prefix = (alias == null || alias.isBlank()) ? "" : alias + ".";
        return "COALESCE(" + prefix + col + ",'CONFIRMED')";
    }

    private String resolveBookingStatusColumn(Connection cnx) throws SQLException {
        if (hasColumn(cnx, "bookings", "bookingStatus")) return "bookingStatus";
        if (hasColumn(cnx, "bookings", "statusBk")) return "statusBk";
        if (hasColumn(cnx, "bookings", "booking_status")) return "booking_status";
        return null;
    }

    private boolean hasReminderColumn(String reminderColumn) {
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            return hasColumn(cnx, "bookings", reminderColumn);
        } catch (SQLException ex) {
            return false;
        }
    }

    private boolean hasColumn(Connection cnx, String table, String column) throws SQLException {
        String cacheKey = table + "." + column;
        Boolean cached = columnCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                boolean exists = rs.next();
                columnCache.put(cacheKey, exists);
                return exists;
            }
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private BookingStatus parseBookingStatus(String value) {
        if (value == null || value.isBlank()) {
            return BookingStatus.CONFIRMED;
        }
        try {
            return BookingStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return BookingStatus.CONFIRMED;
        }
    }

    private EventStatus parseEventStatus(String value) {
        if (value == null || value.isBlank()) {
            return EventStatus.PUBLISHED;
        }
        try {
            return EventStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return EventStatus.PUBLISHED;
        }
    }

    private static class EventInfo {
        int capacity;
        LocalDateTime startDate;
        double ticketPrice;
        String currencyCode;
        EventStatus status;
    }

    @FunctionalInterface
    private interface PreparedStatementConfigurer {
        void configure(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlBuilder {
        String build(Connection cnx) throws SQLException;
    }
}
