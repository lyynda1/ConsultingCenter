package com.advisora.Services.investment;

import com.advisora.Model.investment.Investment;
import com.advisora.Services.IService;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InvestmentService implements IService<Investment> {

    @Override
    public void ajouter(Investment investment) {
        String query = "INSERT INTO investments (commentaireInv, dureeInv, bud_minInv, bud_maxInv, CurrencyInv, idProj, idUser) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pst.setString(1, investment.getCommentaireInv());
            pst.setTime(2, investment.getDureeInv());
            pst.setDouble(3, investment.getBud_minInv());
            pst.setDouble(4, investment.getBud_maxInv());
            pst.setString(5, investment.getCurrencyInv());
            pst.setInt(6, investment.getIdProj());
            pst.setInt(7, investment.getIdUser());
            int affectedRows = pst.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        investment.setIdInv(generatedKeys.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error adding investment: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Investment> afficher() {
        List<Investment> investments = new ArrayList<>();
        try (Connection conn = MyConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + resolveInvestmentsTable(conn))) {
            while (rs.next()) {
                investments.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching investments: " + e.getMessage(), e);
        }
        return investments;
    }

    @Override
    public void modifier(Investment investment) {
        String query = "UPDATE investments SET commentaireInv = ?, dureeInv = ?, bud_minInv = ?, " +
                "bud_maxInv = ?, CurrencyInv = ?, idProj = ?, idUser = ? WHERE idInv = ?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setString(1, investment.getCommentaireInv());
            pst.setTime(2, investment.getDureeInv());
            pst.setDouble(3, investment.getBud_minInv());
            pst.setDouble(4, investment.getBud_maxInv());
            pst.setString(5, investment.getCurrencyInv());
            pst.setInt(6, investment.getIdProj());
            pst.setInt(7, investment.getIdUser());
            pst.setInt(8, investment.getIdInv());
            pst.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating investment: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(Investment investment) {
        String query = "DELETE FROM investments WHERE idInv = ?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, investment.getIdInv());
            pst.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting investment: " + e.getMessage(), e);
        }
    }

    public Investment getInvestmentById(int idInv) {
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement("SELECT * FROM " + resolveInvestmentsTable(conn) + " WHERE idInv = ?")) {
            pst.setInt(1, idInv);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching investment by ID: " + e.getMessage(), e);
        }
    }

    public List<Investment> getInvestmentsByProject(int idProj) {
        List<Investment> investments = new ArrayList<>();
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement("SELECT * FROM " + resolveInvestmentsTable(conn) + " WHERE idProj = ?")) {
            pst.setInt(1, idProj);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) investments.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching investments by project: " + e.getMessage(), e);
        }
        return investments;
    }

    public List<Investment> getInvestmentsByUser(int idUser) {
        List<Investment> investments = new ArrayList<>();
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement("SELECT * FROM " + resolveInvestmentsTable(conn) + " WHERE idUser = ?")) {
            pst.setInt(1, idUser);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) investments.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching investments by user: " + e.getMessage(), e);
        }
        return investments;
    }

    /** Investissements visibles pour un client : crÃ©Ã©s par lui (idUser) ou liÃ©s Ã  des projets dont il est propriÃ©taire (idClient). */
    public List<Investment> getInvestmentsForClient(int userId) {
        List<Investment> investments = new ArrayList<>();
        SQLException last = null;
        try (Connection conn = MyConnection.getInstance().getConnection()) {
            String invTable = resolveInvestmentsTable(conn);
            String projTable = resolveProjectsTable(conn);
            String query = "SELECT i.* FROM " + invTable + " i " +
                    "LEFT JOIN " + projTable + " p ON i.idProj = p.idProj " +
                    "WHERE i.idUser = ? OR p.idClient = ? " +
                    "ORDER BY i.idInv";
            try (PreparedStatement pst = conn.prepareStatement(query)) {
                pst.setInt(1, userId);
                pst.setInt(2, userId);
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) investments.add(mapRow(rs));
                }
                return investments;
            }
        } catch (SQLException e) {
            last = e;
        }

        throw new RuntimeException("Error fetching investments for client: "
                + (last == null ? "unknown error" : last.getMessage()), last);
    }

    private String resolveInvestmentsTable(Connection conn) throws SQLException {
        for (String t : List.of("investments", "investment", "Investments")) {
            if (hasTable(conn, t)) return t;
        }
        return "investments";
    }

    private String resolveProjectsTable(Connection conn) throws SQLException {
        for (String t : List.of("projects", "Projects", "project")) {
            if (hasTable(conn, t)) return t;
        }
        return "projects";
    }

    private boolean hasTable(Connection conn, String tableName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Investment mapRow(ResultSet rs) throws SQLException {
        return new Investment(
                rs.getInt("idInv"),
                rs.getString("commentaireInv"),
                rs.getTime("dureeInv"),
                rs.getDouble("bud_minInv"),
                rs.getDouble("bud_maxInv"),
                rs.getString("CurrencyInv"),
                rs.getInt("idProj"),
                rs.getInt("idUser")
        );
    }
}
