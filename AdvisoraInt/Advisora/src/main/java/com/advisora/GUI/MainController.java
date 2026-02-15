package com.advisora.GUI;

import com.advisora.GUI.Investissement.InvestmentListController;
import com.advisora.GUI.Transaction.TransactionListController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * Contrôleur principal : une seule fenêtre, sidebar fixe.
 * Clic sur "Gestion Investissements" ou "Gestion Transactions" = on remplace
 * uniquement le contenu central (plus de nouvelle fenêtre = plus de rectangle gris).
 */
public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button btnNavInvestissements;

    @FXML
    public void initialize() {
        showInvestissements();
    }

    @FXML
    private void handleOpenInvestissements() {
        showInvestissements();
    }

    /** Affiche la vue Investissements dans la zone centrale (même fenêtre, pas de rectangle gris). */
    public void showInvestissements() {
        try {
            contentArea.getChildren().clear();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/investissement/investissementContent.fxml"));
            Parent root = loader.load();
            InvestmentListController ctrl = loader.getController();
            ctrl.setMainController(this);
            contentArea.getChildren().setAll(root);
            setNavActive(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Affiche la vue Transactions dans la zone centrale (même fenêtre, pas de rectangle gris). */
    public void showTransactions() {
        try {
            contentArea.getChildren().clear();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/transaction/transactionContent.fxml"));
            Parent root = loader.load();
            TransactionListController ctrl = loader.getController();
            ctrl.setMainController(this);
            contentArea.getChildren().setAll(root);
            setNavActive(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setNavActive(boolean investissementsActive) {
        if (btnNavInvestissements != null) {
            btnNavInvestissements.getStyleClass().removeAll("nav-active");
        }
    }
}
