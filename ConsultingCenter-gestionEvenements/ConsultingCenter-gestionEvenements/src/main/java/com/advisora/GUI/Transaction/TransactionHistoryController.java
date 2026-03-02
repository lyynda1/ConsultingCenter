package com.advisora.GUI.Transaction;

import com.advisora.Model.investment.Investment;
import com.advisora.Model.investment.Transaction;
import com.advisora.Services.investment.InvestmentService;
import com.advisora.Services.investment.TransactionService;
import com.advisora.Services.user.SessionContext;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class TransactionHistoryController {

    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, Number> colId;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, Number> colMontant;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, String> colStatut;
    @FXML private TableColumn<Transaction, String> colInv;

    private final TransactionService transactionService = new TransactionService();
    private final InvestmentService investmentService = new InvestmentService();
    private Map<Integer, Investment> investmentById = Collections.emptyMap();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadData();
    }

    private void setupTableColumns() {
        if (colId != null) {
            colId.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getIdTransac()));
            if (SessionContext.isClient()) {
                colId.setVisible(false);
            }
        }
        if (colDate != null) {
            colDate.setCellValueFactory(data -> {
                var d = data.getValue().getDateTransac();
                return new javafx.beans.property.SimpleStringProperty(d == null ? "-" : d.toString());
            });
        }
        if (colMontant != null) {
            colMontant.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getMontantTransac()));
        }
        if (colType != null) {
            colType.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(safe(data.getValue().getType())));
        }
        if (colStatut != null) {
            colStatut.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                    data.getValue().getStatut() == null ? "" : data.getValue().getStatut().name()
            ));
        }
        if (colInv != null) {
            colInv.setCellValueFactory(data -> {
                Transaction t = data.getValue();
                Investment inv = investmentById.get(t.getIdInv());
                String name = (inv != null && inv.getCommentaireInv() != null)
                        ? inv.getCommentaireInv()
                        : ("Investissement #" + t.getIdInv());
                return new javafx.beans.property.SimpleStringProperty(name);
            });
        }
    }

    private void loadData() {
        var invList = SessionContext.isClient()
                ? investmentService.getInvestmentsForClient(SessionContext.getCurrentUserId())
                : investmentService.afficher();
        investmentById = invList.stream()
                .collect(Collectors.toMap(Investment::getIdInv, inv -> inv, (a, b) -> a));
        var all = javafx.collections.FXCollections.observableArrayList(
                SessionContext.isClient()
                        ? transactionService.getTransactionsForClient(SessionContext.getCurrentUserId())
                        : transactionService.afficher());
        transactionTable.setItems(all);
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) transactionTable.getScene().getWindow();
        stage.close();
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
}

