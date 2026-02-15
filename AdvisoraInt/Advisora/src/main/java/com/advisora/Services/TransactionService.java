package com.advisora.Services;

import com.advisora.Model.Transaction;
import com.advisora.enums.transactionStatut;
import com.advisora.Util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TransactionService implements IService<Transaction> {

    @Override
    public void ajouter(Transaction transaction) {
        String query = "INSERT INTO transaction (DateTransac, MontantTransac, type, statut, idInv) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            pst.setDate(1, transaction.getDateTransac());
            pst.setDouble(2, transaction.getMontantTransac());
            pst.setString(3, transaction.getType());
            pst.setString(4, transaction.getStatut().name());
            pst.setInt(5, transaction.getIdInv());

            int affectedRows = pst.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        transaction.setIdTransac(generatedKeys.getInt(1));
                    }
                }
                System.out.println("Transaction added successfully with ID: " + transaction.getIdTransac());
            }
        } catch (SQLException e) {
            System.err.println("Error adding transaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<Transaction> afficher() {
        List<Transaction> transactions = new ArrayList<>();
        String query = "SELECT * FROM transaction";

        try (Connection conn = DB.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {

            while (rs.next()) {
                Transaction transaction = new Transaction(
                        rs.getInt("idTransac"),
                        rs.getDate("DateTransac"),
                        rs.getDouble("MontantTransac"),
                        rs.getString("type"),
                        transactionStatut.valueOf(rs.getString("statut")),
                        rs.getInt("idInv")
                );
                transactions.add(transaction);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching transactions: " + e.getMessage());
            e.printStackTrace();
        }

        return transactions;
    }

    @Override
    public void modifier(Transaction transaction) {
        String query = "UPDATE transaction SET DateTransac = ?, MontantTransac = ?, type = ?, " +
                "statut = ?, idInv = ? WHERE idTransac = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setDate(1, transaction.getDateTransac());
            pst.setDouble(2, transaction.getMontantTransac());
            pst.setString(3, transaction.getType());
            pst.setString(4, transaction.getStatut().name());
            pst.setInt(5, transaction.getIdInv());
            pst.setInt(6, transaction.getIdTransac());

            int affectedRows = pst.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Transaction updated successfully!");
            } else {
                System.out.println("No transaction found with ID: " + transaction.getIdTransac());
            }
        } catch (SQLException e) {
            System.err.println("Error updating transaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void supprimer(Transaction transaction) {
        String query = "DELETE FROM transaction WHERE idTransac = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, transaction.getIdTransac());

            int affectedRows = pst.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Transaction deleted successfully!");
            } else {
                System.out.println("No transaction found with ID: " + transaction.getIdTransac());
            }
        } catch (SQLException e) {
            System.err.println("Error deleting transaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Additional utility methods

    public Transaction getTransactionById(int idTransac) {
        String query = "SELECT * FROM transaction WHERE idTransac = ?";
        Transaction transaction = null;

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, idTransac);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    transaction = new Transaction(
                            rs.getInt("idTransac"),
                            rs.getDate("DateTransac"),
                            rs.getDouble("MontantTransac"),
                            rs.getString("type"),
                            transactionStatut.valueOf(rs.getString("statut")),
                            rs.getInt("idInv")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching transaction by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return transaction;
    }

    public List<Transaction> getTransactionsByInvestment(int idInv) {
        List<Transaction> transactions = new ArrayList<>();
        String query = "SELECT * FROM transaction WHERE idInv = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, idInv);

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Transaction transaction = new Transaction(
                            rs.getInt("idTransac"),
                            rs.getDate("DateTransac"),
                            rs.getDouble("MontantTransac"),
                            rs.getString("type"),
                            transactionStatut.valueOf(rs.getString("statut")),
                            rs.getInt("idInv")
                    );
                    transactions.add(transaction);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching transactions by investment: " + e.getMessage());
            e.printStackTrace();
        }

        return transactions;
    }

    public List<Transaction> getTransactionsByStatus(transactionStatut statut) {
        List<Transaction> transactions = new ArrayList<>();
        String query = "SELECT * FROM transaction WHERE statut = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setString(1, statut.name());

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Transaction transaction = new Transaction(
                            rs.getInt("idTransac"),
                            rs.getDate("DateTransac"),
                            rs.getDouble("MontantTransac"),
                            rs.getString("type"),
                            transactionStatut.valueOf(rs.getString("statut")),
                            rs.getInt("idInv")
                    );
                    transactions.add(transaction);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching transactions by status: " + e.getMessage());
            e.printStackTrace();
        }

        return transactions;
    }

    public List<Transaction> getTransactionsByType(String type) {
        List<Transaction> transactions = new ArrayList<>();
        String query = "SELECT * FROM transaction WHERE type = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setString(1, type);

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Transaction transaction = new Transaction(
                            rs.getInt("idTransac"),
                            rs.getDate("DateTransac"),
                            rs.getDouble("MontantTransac"),
                            rs.getString("type"),
                            transactionStatut.valueOf(rs.getString("statut")),
                            rs.getInt("idInv")
                    );
                    transactions.add(transaction);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching transactions by type: " + e.getMessage());
            e.printStackTrace();
        }

        return transactions;
    }

    public double getTotalAmountByInvestment(int idInv) {
        String query = "SELECT SUM(MontantTransac) as total FROM transaction WHERE idInv = ? AND statut = 'SUCCESS'";
        double total = 0.0;

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, idInv);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    total = rs.getDouble("total");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error calculating total amount: " + e.getMessage());
            e.printStackTrace();
        }

        return total;
    }

    public void updateTransactionStatus(int idTransac, transactionStatut newStatut) {
        String query = "UPDATE transaction SET statut = ? WHERE idTransac = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setString(1, newStatut.name());
            pst.setInt(2, idTransac);

            int affectedRows = pst.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Transaction status updated successfully!");
            } else {
                System.out.println("No transaction found with ID: " + idTransac);
            }
        } catch (SQLException e) {
            System.err.println("Error updating transaction status: " + e.getMessage());
            e.printStackTrace();
        }
    }
}