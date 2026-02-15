package com.advisora.Services;

import com.advisora.Model.Investment;
import com.advisora.Util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InvestmentService implements IService<Investment> {

    @Override
    public void ajouter(Investment investment) {
        String query = "INSERT INTO investments (commentaireInv, dureeInv, bud_minInv, bud_maxInv, CurrencyInv, idProj, idUser) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DB.getConnection();
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
                System.out.println("Investment added successfully with ID: " + investment.getIdInv());
            }
        } catch (SQLException e) {
            System.err.println("Error adding investment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<Investment> afficher() {
        List<Investment> investments = new ArrayList<>();
        String query = "SELECT * FROM investments";

        try (Connection conn = DB.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {

            while (rs.next()) {
                Investment investment = new Investment(
                        rs.getInt("idInv"),
                        rs.getString("commentaireInv"),
                        rs.getTime("dureeInv"),
                        rs.getDouble("bud_minInv"),
                        rs.getDouble("bud_maxInv"),
                        rs.getString("CurrencyInv"),
                        rs.getInt("idProj"),
                        rs.getInt("idUser")
                );
                investments.add(investment);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching investments: " + e.getMessage());
            e.printStackTrace();
        }

        return investments;
    }

    @Override
    public void modifier(Investment investment) {
        String query = "UPDATE investments SET commentaireInv = ?, dureeInv = ?, bud_minInv = ?, " +
                "bud_maxInv = ?, CurrencyInv = ?, idProj = ?, idUser = ? WHERE idInv = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setString(1, investment.getCommentaireInv());
            pst.setTime(2, investment.getDureeInv());
            pst.setDouble(3, investment.getBud_minInv());
            pst.setDouble(4, investment.getBud_maxInv());
            pst.setString(5, investment.getCurrencyInv());
            pst.setInt(6, investment.getIdProj());
            pst.setInt(7, investment.getIdUser());
            pst.setInt(8, investment.getIdInv());

            int affectedRows = pst.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Investment updated successfully!");
            } else {
                System.out.println("No investment found with ID: " + investment.getIdInv());
            }
        } catch (SQLException e) {
            System.err.println("Error updating investment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void supprimer(Investment investment) {
        String query = "DELETE FROM investments WHERE idInv = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, investment.getIdInv());

            int affectedRows = pst.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("Investment deleted successfully!");
            } else {
                System.out.println("No investment found with ID: " + investment.getIdInv());
            }
        } catch (SQLException e) {
            System.err.println("Error deleting investment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Additional utility methods

    public Investment getInvestmentById(int idInv) {
        String query = "SELECT * FROM investments WHERE idInv = ?";
        Investment investment = null;

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, idInv);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    investment = new Investment(
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
        } catch (SQLException e) {
            System.err.println("Error fetching investment by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return investment;
    }

    public List<Investment> getInvestmentsByProject(int idProj) {
        List<Investment> investments = new ArrayList<>();
        String query = "SELECT * FROM investments WHERE idProj = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, idProj);

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Investment investment = new Investment(
                            rs.getInt("idInv"),
                            rs.getString("commentaireInv"),
                            rs.getTime("dureeInv"),
                            rs.getDouble("bud_minInv"),
                            rs.getDouble("bud_maxInv"),
                            rs.getString("CurrencyInv"),
                            rs.getInt("idProj"),
                            rs.getInt("idUser")
                    );
                    investments.add(investment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching investments by project: " + e.getMessage());
            e.printStackTrace();
        }

        return investments;
    }

    public List<Investment> getInvestmentsByUser(int idUser) {
        List<Investment> investments = new ArrayList<>();
        String query = "SELECT * FROM investments WHERE idUser = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement pst = conn.prepareStatement(query)) {

            pst.setInt(1, idUser);

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Investment investment = new Investment(
                            rs.getInt("idInv"),
                            rs.getString("commentaireInv"),
                            rs.getTime("dureeInv"),
                            rs.getDouble("bud_minInv"),
                            rs.getDouble("bud_maxInv"),
                            rs.getString("CurrencyInv"),
                            rs.getInt("idProj"),
                            rs.getInt("idUser")
                    );
                    investments.add(investment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching investments by user: " + e.getMessage());
            e.printStackTrace();
        }

        return investments;
    }
}