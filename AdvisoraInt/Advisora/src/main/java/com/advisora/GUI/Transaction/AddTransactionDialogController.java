package com.advisora.GUI.Transaction;

import com.advisora.Model.Transaction;
import com.advisora.Services.TransactionService;
import com.advisora.enums.transactionStatut;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.sql.Date;

public class AddTransactionDialogController {

    @FXML private Label dialogTitle;
    @FXML private Button submitButton;
    @FXML private TextField dateField;
    @FXML private TextField montantField;
    @FXML private TextField typeField;
    @FXML private ComboBox<transactionStatut> statutCombo;
    @FXML private TextField idInvField;
    @FXML private HBox dragHandle;

    private Transaction editingTransaction = null;
    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};
    private final TransactionService service = new TransactionService();

    @FXML
    public void initialize() {
        statutCombo.getItems().setAll(transactionStatut.values());
        statutCombo.getSelectionModel().select(transactionStatut.PENDING);
    }

    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable onClose) { this.onClose = onClose; }
    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    /** Mode ajout : formulaire vide, titre "Nouvelle Transaction", bouton "Ajouter". */
    public void initForAdd() {
        editingTransaction = null;
        if (dialogTitle != null) dialogTitle.setText("Nouvelle Transaction");
        if (submitButton != null) submitButton.setText("Ajouter");
        clearFields();
    }

    /** Mode modification : pré-remplit le formulaire avec la transaction. */
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
        idInvField.setText(String.valueOf(t.getIdInv()));
    }

    private void clearFields() {
        if (dateField != null) dateField.clear();
        if (montantField != null) montantField.clear();
        if (typeField != null) typeField.clear();
        if (statutCombo != null) statutCombo.getSelectionModel().select(transactionStatut.PENDING);
        if (idInvField != null) idInvField.clear();
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
            new Alert(Alert.AlertType.ERROR, "Montant doit être un nombre.").showAndWait();
            return;
        }
        String type = typeField.getText() == null ? "" : typeField.getText().trim();
        if (type.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Veuillez saisir le type.").showAndWait();
            return;
        }
        transactionStatut statut = statutCombo.getValue() == null ? transactionStatut.PENDING : statutCombo.getValue();
        int idInv;
        try {
            idInv = Integer.parseInt(idInvField.getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "ID Investissement doit être un entier.").showAndWait();
            return;
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
}
