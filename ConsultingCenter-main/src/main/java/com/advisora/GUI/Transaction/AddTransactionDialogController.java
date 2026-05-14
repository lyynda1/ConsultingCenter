package com.advisora.GUI.Transaction;

import com.advisora.Model.investment.Investment;
import com.advisora.Model.investment.Transaction;
import com.advisora.Services.investment.InvestmentService;
import com.advisora.Services.investment.TransactionService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.transactionStatut;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.sql.Date;
import java.util.List;

public class AddTransactionDialogController {

    @FXML private Label dialogTitle;
    @FXML private Button submitButton;
    @FXML private TextField dateField;
    @FXML private TextField montantField;
    @FXML private TextField typeField;
    @FXML private VBox statutRow;
    @FXML private ComboBox<transactionStatut> statutCombo;
    @FXML private VBox idInvRow;
    @FXML private VBox clientInvestmentRow;
    @FXML private ComboBox<Investment> investmentCombo;
    @FXML private ComboBox<Investment> idInvCombo;
    @FXML private HBox dragHandle;

    private Transaction editingTransaction = null;
    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};
    private final TransactionService service = new TransactionService();
    private final InvestmentService investmentService = new InvestmentService();

    @FXML
    public void initialize() {
        statutCombo.getItems().setAll(transactionStatut.values());
        statutCombo.getSelectionModel().select(transactionStatut.PENDING);
        configureInvestmentCombos();

        if (SessionContext.isClient()) {
            if (statutRow != null) {
                statutRow.setVisible(false);
                statutRow.setManaged(false);
            }
            if (idInvRow != null) {
                idInvRow.setVisible(false);
                idInvRow.setManaged(false);
            }
            if (clientInvestmentRow != null) {
                clientInvestmentRow.setVisible(true);
                clientInvestmentRow.setManaged(true);
            }
            loadClientInvestments();
        } else {
            if (clientInvestmentRow != null) {
                clientInvestmentRow.setVisible(false);
                clientInvestmentRow.setManaged(false);
            }
            loadAllInvestments();
        }
    }

    private void configureInvestmentCombos() {
        StringConverter<Investment> converter = new StringConverter<>() {
            @Override
            public String toString(Investment inv) {
                if (inv == null) return "";
                String label = safe(inv.getCommentaireInv());
                if (label.isBlank()) return "Investissement #" + inv.getIdInv();
                if (label.length() > 55) label = label.substring(0, 52) + "...";
                return "#" + inv.getIdInv() + " - " + label;
            }

            @Override
            public Investment fromString(String s) {
                return null;
            }
        };

        if (investmentCombo != null) investmentCombo.setConverter(converter);
        if (idInvCombo != null) idInvCombo.setConverter(converter);
    }

    private void loadClientInvestments() {
        if (investmentCombo == null) return;
        int userId = SessionContext.getCurrentUserId();
        List<Investment> list = investmentService.getInvestmentsForClient(userId);
        investmentCombo.getItems().setAll(list);
    }

    private void loadAllInvestments() {
        if (idInvCombo == null) return;
        List<Investment> list = investmentService.afficher();
        idInvCombo.getItems().setAll(list);
    }

    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable onClose) { this.onClose = onClose; }
    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    public void initForAdd() {
        editingTransaction = null;
        if (dialogTitle != null) dialogTitle.setText("Nouvelle Transaction");
        if (submitButton != null) submitButton.setText("Ajouter");
        clearFields();
        if (SessionContext.isClient()) {
            loadClientInvestments();
        } else {
            loadAllInvestments();
        }
    }

    public void initForEdit(Transaction t) {
        editingTransaction = t;
        if (dialogTitle != null) dialogTitle.setText("Modifier la transaction");
        if (submitButton != null) submitButton.setText("Modifier");
        if (t == null) {
            clearFields();
            return;
        }

        dateField.setText(t.getDateTransac() == null ? "" : t.getDateTransac().toString());
        montantField.setText(String.valueOf(t.getMontantTransac()));
        typeField.setText(t.getType() == null ? "" : t.getType());
        if (t.getStatut() != null) statutCombo.getSelectionModel().select(t.getStatut());

        if (SessionContext.isClient()) {
            if (investmentCombo != null) {
                loadClientInvestments();
                investmentCombo.getItems().stream()
                        .filter(inv -> inv.getIdInv() == t.getIdInv())
                        .findFirst()
                        .ifPresent(inv -> investmentCombo.getSelectionModel().select(inv));
            }
        } else {
            if (idInvCombo != null) {
                loadAllInvestments();
                idInvCombo.getItems().stream()
                        .filter(inv -> inv.getIdInv() == t.getIdInv())
                        .findFirst()
                        .ifPresent(inv -> idInvCombo.getSelectionModel().select(inv));
            }
        }
    }

    private void clearFields() {
        if (dateField != null) dateField.clear();
        if (montantField != null) montantField.clear();
        if (typeField != null) typeField.clear();
        if (statutCombo != null) statutCombo.getSelectionModel().select(transactionStatut.PENDING);
        if (idInvCombo != null) idInvCombo.getSelectionModel().clearSelection();
        if (investmentCombo != null) investmentCombo.getSelectionModel().clearSelection();
    }

    @FXML
    private void close() {
        onClose.run();
    }

    @FXML
    private void save() {
        Date date;
        try {
            date = Date.valueOf(dateField.getText().trim());
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Date invalide. Utilisez yyyy-MM-dd").showAndWait();
            return;
        }

        double montant;
        try {
            montant = Double.parseDouble(montantField.getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Montant doit etre un nombre.").showAndWait();
            return;
        }

        String type = typeField.getText() == null ? "" : typeField.getText().trim();
        if (type.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Veuillez saisir le type.").showAndWait();
            return;
        }

        transactionStatut statut;
        if (SessionContext.isClient()) {
            statut = (editingTransaction != null) ? editingTransaction.getStatut() : transactionStatut.PENDING;
        } else {
            statut = statutCombo.getValue() == null ? transactionStatut.PENDING : statutCombo.getValue();
        }

        int idInv;
        if (SessionContext.isClient()) {
            Investment sel = investmentCombo.getValue();
            if (sel == null) {
                new Alert(Alert.AlertType.ERROR, "Veuillez choisir un investissement.").showAndWait();
                return;
            }
            idInv = sel.getIdInv();
        } else {
            Investment sel = idInvCombo == null ? null : idInvCombo.getValue();
            if (sel == null) {
                new Alert(Alert.AlertType.ERROR, "Veuillez choisir un ID investissement dans la liste.").showAndWait();
                return;
            }
            idInv = sel.getIdInv();
        }

        if (editingTransaction != null) {
            editingTransaction.setDateTransac(date);
            editingTransaction.setMontantTransac(montant);
            editingTransaction.setType(type);
            editingTransaction.setStatut(statut);
            editingTransaction.setIdInv(idInv);
            service.modifier(editingTransaction);
        } else {
            Transaction t = new Transaction(date, montant, type, statut, idInv);
            service.ajouter(t);
        }
        onSaved.run();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
