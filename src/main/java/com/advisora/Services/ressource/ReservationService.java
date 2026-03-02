package com.advisora.Services.ressource;

import com.advisora.Model.ressource.Booking;
import com.advisora.Model.ressource.Ressource;
import com.advisora.enums.ProjectStatus;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReservationService implements IReservationService {
    private final RessourceService ressourceService = new RessourceService();
    private String qtyColumnCache;

    @Override
    public void reserveForClient(int clientId, int idRs, int quantity, Integer projectIdOrNull) {
        if (clientId <= 0) {
            throw new IllegalArgumentException("Client invalide.");
        }
        if (idRs <= 0) {
            throw new IllegalArgumentException("Ressource invalide.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantite > 0 obligatoire.");
        }

        Ressource res = ressourceService.getById(idRs);
        if (res == null) {
            throw new IllegalArgumentException("Ressource introuvable.");
        }

        int available = ressourceService.getAvailableStock(idRs);
        if (quantity > available) {
            throw new IllegalArgumentException("Stock insuffisant. Disponible: " + available);
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                int projectId = resolveOrCreateProject(cnx, clientId, projectIdOrNull);
                insertProjectResourceRows(cnx, projectId, idRs, quantity);
                cnx.commit();
            } catch (Exception e) {
                cnx.rollback();
                throw e;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur reservation: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateReservationForClient(int clientId, int idProj, int idRs, int newQuantity) {
        updateReservationInternal(clientId, idProj, idRs, newQuantity, true);
    }

    @Override
    public void updateReservationAsManager(int idProj, int idRs, int newQuantity) {
        updateReservationInternal(null, idProj, idRs, newQuantity, false);
    }

    @Override
    public void deleteReservationForClient(int clientId, int idProj, int idRs) {
        updateReservationInternal(clientId, idProj, idRs, 0, true);
    }

    @Override
    public void deleteReservationAsManager(int idProj, int idRs) {
        updateReservationInternal(null, idProj, idRs, 0, false);
    }

    @Override
    public List<Booking> listClientReservations(int clientId) {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        String qtyExpr = qtyColumn == null ? "COUNT(*)" : "COALESCE(SUM(pr." + qtyColumn + "),0)";
        String sql = "SELECT p.idProj, p.titleProj, r.idRs, r.nomRs, cf.nomFr, "
                + "CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS clientName, "
                + qtyExpr + " AS qte "
                + "FROM projects p "
                + "JOIN project_resources pr ON pr.idProj = p.idProj "
                + "JOIN resources r ON r.idRs = pr.idRs "
                + "JOIN cataloguefournisseur cf ON cf.idFr = r.idFr "
                + "LEFT JOIN `user` u ON u.idUser = p.idClient "
                + "WHERE p.idClient = ? "
                + "GROUP BY p.idProj, p.titleProj, r.idRs, r.nomRs, cf.nomFr, u.PrenomUser, u.nomUser "
                + "ORDER BY p.idProj DESC";

        return queryBookings(sql, ps -> ps.setInt(1, clientId));
    }

    @Override
    public List<Booking> listAllReservations() {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        String qtyExpr = qtyColumn == null ? "COUNT(*)" : "COALESCE(SUM(pr." + qtyColumn + "),0)";
        String sql = "SELECT p.idProj, p.titleProj, r.idRs, r.nomRs, cf.nomFr, "
                + "CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS clientName, "
                + qtyExpr + " AS qte "
                + "FROM projects p "
                + "JOIN project_resources pr ON pr.idProj = p.idProj "
                + "JOIN resources r ON r.idRs = pr.idRs "
                + "JOIN cataloguefournisseur cf ON cf.idFr = r.idFr "
                + "LEFT JOIN `user` u ON u.idUser = p.idClient "
                + "GROUP BY p.idProj, p.titleProj, r.idRs, r.nomRs, cf.nomFr, u.PrenomUser, u.nomUser "
                + "ORDER BY p.idProj DESC";

        return queryBookings(sql, null);
    }

    private List<Booking> queryBookings(String sql, PreparedStatementConfigurer configurer) {
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            if (configurer != null) {
                configurer.configure(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Booking> list = new ArrayList<>();
                while (rs.next()) {
                    Booking b = new Booking();
                    b.setIdProj(rs.getInt("idProj"));
                    b.setProjectTitle(rs.getString("titleProj"));
                    b.setIdRs(rs.getInt("idRs"));
                    b.setResourceName(rs.getString("nomRs"));
                    b.setFournisseurName(rs.getString("nomFr"));
                    b.setQuantity(rs.getInt("qte"));
                    b.setClientName(rs.getString("clientName"));
                    list.add(b);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture reservations: " + e.getMessage(), e);
        }
    }

    private int resolveOrCreateProject(Connection cnx, int clientId, Integer projectIdOrNull) throws SQLException {
        if (projectIdOrNull != null && projectIdOrNull > 0) {
            String sql = "SELECT idProj FROM projects WHERE idProj = ? AND idClient = ?";
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, projectIdOrNull);
                ps.setInt(2, clientId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return projectIdOrNull;
                    }
                }
            }
            throw new IllegalArgumentException("Projet client introuvable.");
        }

        String findSql = "SELECT idProj FROM projects WHERE idClient = ? ORDER BY idProj DESC LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(findSql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        String insertSql = "INSERT INTO projects (titleProj, descriptionProj, budgetProj, typeProj, stateProj, createdAtProj, updatedAtProj, avancementProj, idClient) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Reservation Ressources");
            ps.setString(2, "Cree automatiquement pour reservation");
            ps.setDouble(3, 0.0);
            ps.setString(4, "RESOURCE");
            ps.setString(5, ProjectStatus.PENDING.name());
            ps.setDouble(6, 0.0);
            ps.setInt(7, clientId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        throw new IllegalStateException("Impossible de creer projet de reservation.");
    }

    private void insertProjectResourceRows(Connection cnx, int idProj, int idRs, int quantity) throws SQLException {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        if (qtyColumn == null) {
            String sql = "INSERT INTO project_resources (idProj, idRs) VALUES (?, ?)";
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                for (int i = 0; i < quantity; i++) {
                    ps.setInt(1, idProj);
                    ps.setInt(2, idRs);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return;
        }

        String sql = "INSERT INTO project_resources (idProj, idRs, " + qtyColumn + ") VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE " + qtyColumn + " = " + qtyColumn + " + VALUES(" + qtyColumn + ")";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            ps.setInt(2, idRs);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    private void updateReservationInternal(Integer clientIdOrNull, int idProj, int idRs, int newQuantity, boolean enforceClientOwner) {
        if (idProj <= 0 || idRs <= 0) {
            throw new IllegalArgumentException("Reservation invalide.");
        }
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantite >= 0 obligatoire.");
        }

        Ressource res = ressourceService.getById(idRs);
        if (res == null) {
            throw new IllegalArgumentException("Ressource introuvable.");
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                assertReservationOwnership(cnx, idProj, idRs, clientIdOrNull, enforceClientOwner);

                int currentQty = currentReservedForProjectResource(cnx, idProj, idRs);
                int availableNow = ressourceService.getAvailableStock(idRs);
                int maxAllowed = currentQty + availableNow;
                if (newQuantity > maxAllowed) {
                    throw new IllegalArgumentException("Stock insuffisant. Maximum autorise: " + maxAllowed);
                }

                replaceReservationQuantity(cnx, idProj, idRs, newQuantity);
                cnx.commit();
            } catch (Exception e) {
                cnx.rollback();
                throw e;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification reservation: " + e.getMessage(), e);
        }
    }

    private void assertReservationOwnership(Connection cnx, int idProj, int idRs, Integer clientIdOrNull, boolean enforceClientOwner) throws SQLException {
        String sql = "SELECT p.idClient, COUNT(*) AS cnt "
                + "FROM projects p "
                + "JOIN project_resources pr ON pr.idProj = p.idProj "
                + "WHERE p.idProj = ? AND pr.idRs = ? "
                + "GROUP BY p.idClient";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            ps.setInt(2, idRs);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Reservation introuvable.");
                }
                int owner = rs.getInt("idClient");
                if (enforceClientOwner && clientIdOrNull != null && owner != clientIdOrNull) {
                    throw new IllegalStateException("Acces refuse sur cette reservation.");
                }
            }
        }
    }

    private int currentReservedForProjectResource(Connection cnx, int idProj, int idRs) throws SQLException {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        String sql = qtyColumn == null
                ? "SELECT COUNT(*) FROM project_resources WHERE idProj = ? AND idRs = ?"
                : "SELECT COALESCE(SUM(" + qtyColumn + "), 0) FROM project_resources WHERE idProj = ? AND idRs = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            ps.setInt(2, idRs);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void replaceReservationQuantity(Connection cnx, int idProj, int idRs, int newQuantity) throws SQLException {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        String deleteSql = "DELETE FROM project_resources WHERE idProj = ? AND idRs = ?";
        try (PreparedStatement deletePs = cnx.prepareStatement(deleteSql)) {
            deletePs.setInt(1, idProj);
            deletePs.setInt(2, idRs);
            deletePs.executeUpdate();
        }

        if (newQuantity == 0) {
            return;
        }

        if (qtyColumn == null) {
            String insertSql = "INSERT INTO project_resources (idProj, idRs) VALUES (?, ?)";
            try (PreparedStatement insertPs = cnx.prepareStatement(insertSql)) {
                for (int i = 0; i < newQuantity; i++) {
                    insertPs.setInt(1, idProj);
                    insertPs.setInt(2, idRs);
                    insertPs.addBatch();
                }
                insertPs.executeBatch();
            }
            return;
        }

        String insertSql = "INSERT INTO project_resources (idProj, idRs, " + qtyColumn + ") VALUES (?, ?, ?)";
        try (PreparedStatement insertPs = cnx.prepareStatement(insertSql)) {
            insertPs.setInt(1, idProj);
            insertPs.setInt(2, idRs);
            insertPs.setInt(3, newQuantity);
            insertPs.executeUpdate();
        }
    }

    private String detectProjectResourcesQuantityColumn() {
        if (qtyColumnCache != null) {
            return qtyColumnCache.equals("__NONE__") ? null : qtyColumnCache;
        }

        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='project_resources'";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String col = rs.getString(1);
                if ("quantite".equalsIgnoreCase(col)
                        || "quantity".equalsIgnoreCase(col)
                        || "qty".equalsIgnoreCase(col)
                        || "qtyAllocated".equalsIgnoreCase(col)) {
                    qtyColumnCache = col;
                    return qtyColumnCache;
                }
            }
            qtyColumnCache = "__NONE__";
            return null;
        } catch (SQLException e) {
            qtyColumnCache = "__NONE__";
            return null;
        }
    }

    @FunctionalInterface
    private interface PreparedStatementConfigurer {
        void configure(PreparedStatement ps) throws SQLException;
    }
}

