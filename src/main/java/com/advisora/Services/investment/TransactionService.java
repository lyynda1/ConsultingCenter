package com.advisora.Services.investment;

import com.advisora.Model.investment.Transaction;
import com.advisora.Services.IService;
import com.advisora.enums.transactionStatut;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionService implements IService<Transaction> {

    @Override
    public void ajouter(Transaction transaction) {
        String query = "INSERT INTO transaction (DateTransac, MontantTransac, type, statut, idInv) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pst.setDate(1, transaction.getDateTransac());
            pst.setDouble(2, transaction.getMontantTransac());
            pst.setString(3, transaction.getType());
            pst.setString(4, transaction.getStatut().name());
            pst.setInt(5, transaction.getIdInv());
            int affected = pst.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pst.getGeneratedKeys()) {
                    if (rs.next()) transaction.setIdTransac(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error adding transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Transaction> afficher() {
        List<Transaction> list = new ArrayList<>();
        String query = "SELECT * FROM transaction";
        try (Connection conn = MyConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transactions: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * Transactions visibles pour un client donnÃ© :
     * - liÃ©es Ã  des investissements qu'il a crÃ©Ã©s (i.idUser)
     * - ou Ã  des projets dont il est le client propriÃ©taire (p.idClient).
     */
    public List<Transaction> getTransactionsForClient(int userId) {
        List<Transaction> list = new ArrayList<>();
        String query = "SELECT t.* FROM `transaction` t " +
                "INNER JOIN investments i ON t.idInv = i.idInv " +
                "LEFT JOIN Projects p ON i.idProj = p.idProj " +
                "WHERE i.idUser = ? OR p.idClient = ? " +
                "ORDER BY t.DateTransac DESC, t.idTransac DESC";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, userId);
            pst.setInt(2, userId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transactions for client: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void modifier(Transaction transaction) {
        String query = "UPDATE transaction SET DateTransac = ?, MontantTransac = ?, type = ?, statut = ?, idInv = ? WHERE idTransac = ?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setDate(1, transaction.getDateTransac());
            pst.setDouble(2, transaction.getMontantTransac());
            pst.setString(3, transaction.getType());
            pst.setString(4, transaction.getStatut().name());
            pst.setInt(5, transaction.getIdInv());
            pst.setInt(6, transaction.getIdTransac());
            pst.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(Transaction transaction) {
        String query = "DELETE FROM transaction WHERE idTransac = ?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, transaction.getIdTransac());
            pst.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting transaction: " + e.getMessage(), e);
        }
    }

    /** Ã‰volution du montant des transactions par mois sur les 6 derniers mois. Retourne une Map (libellÃ© mois, somme montants). */
    public Map<String, Double> getTransactionEvolutionLast6Months() {
        Map<String, Double> result = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            String label = ym.getMonthValue() + "/" + ym.getYear();
            result.put(label, 0.0);
        }
        String query = "SELECT YEAR(DateTransac) as y, MONTH(DateTransac) as m, SUM(MontantTransac) as total " +
                "FROM `transaction` WHERE DateTransac >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH) " +
                "GROUP BY YEAR(DateTransac), MONTH(DateTransac) ORDER BY y, m";
        try (Connection conn = MyConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            while (rs.next()) {
                int y = rs.getInt("y");
                int m = rs.getInt("m");
                double total = rs.getDouble("total");
                String key = m + "/" + y;
                if (result.containsKey(key)) {
                    result.put(key, total);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transaction evolution: " + e.getMessage(), e);
        }
        return result;
    }

    /** Ã‰volution mensuelle des transactions sur 6 mois pour un client (investissements du client ou projets oÃ¹ il est propriÃ©taire). */
    public Map<String, Double> getTransactionEvolutionLast6MonthsForClient(int userId) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            String label = ym.getMonthValue() + "/" + ym.getYear();
            result.put(label, 0.0);
        }
        String query = """
                SELECT YEAR(t.DateTransac) AS y, MONTH(t.DateTransac) AS m, SUM(t.MontantTransac) AS total
                FROM `transaction` t
                WHERE t.idInv IN (
                    SELECT i.idInv FROM investments i
                    WHERE i.idUser = ?
                       OR i.idProj IN (SELECT p.idProj FROM Projects p WHERE p.idClient = ?)
                )
                  AND t.DateTransac >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
                GROUP BY YEAR(t.DateTransac), MONTH(t.DateTransac)
                ORDER BY y, m
                """;
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int y = rs.getInt("y");
                    int m = rs.getInt("m");
                    double total = rs.getDouble("total");
                    String key = m + "/" + y;
                    if (result.containsKey(key)) {
                        result.put(key, total);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching client transaction evolution: " + e.getMessage(), e);
        }
        return result;
    }

    /** Somme des montants investis par un client (transactions liÃ©es Ã  ses investissements ou projets). */
    public double getTotalInvestedByClient(int userId) {
        String query = "SELECT COALESCE(SUM(t.MontantTransac), 0) FROM `transaction` t " +
                "INNER JOIN investments i ON t.idInv = i.idInv " +
                "LEFT JOIN projects p ON i.idProj = p.idProj " +
                "WHERE i.idUser = ? OR p.idClient = ?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, userId);
            pst.setInt(2, userId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching total invested by client: " + e.getMessage(), e);
        }
    }

    /** Somme totale de tous les montants des transactions. */
    public double getTotalInvestedGlobal() {
        String query = "SELECT COALESCE(SUM(MontantTransac), 0) FROM `transaction`";
        try (Connection conn = MyConnection.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching total invested: " + e.getMessage(), e);
        }
    }

    public Transaction getTransactionById(int idTransac) {
        String query = "SELECT * FROM transaction WHERE idTransac = ?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {
            pst.setInt(1, idTransac);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transaction by ID: " + e.getMessage(), e);
        }
    }

    private static Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getInt("idTransac"),
                rs.getDate("DateTransac"),
                rs.getDouble("MontantTransac"),
                rs.getString("type"),
                transactionStatut.valueOf(rs.getString("statut")),
                rs.getInt("idInv")
        );
    }
}

