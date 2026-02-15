package com.advisora.GUI.Investissement;

import com.advisora.Model.Investment;
import com.advisora.Services.InvestmentService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.sql.Time;

public class AddInvestissementDialogController {

    @FXML private Label dialogTitle;
    @FXML private Button submitButton;
    @FXML private TextArea commentaireField;
    @FXML private TextField dureeField;
    @FXML private TextField budMinField;
    @FXML private TextField budMaxField;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private TextField idProjField;
    @FXML private TextField idUserField;
    @FXML private HBox dragHandle;

    private Investment editingInvestment = null;
    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};
    private final InvestmentService service = new InvestmentService();

    @FXML
    public void initialize() {
        currencyCombo.getItems().setAll("TND", "EUR", "USD", "GBP");
        currencyCombo.getSelectionModel().select("TND");
    }

    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable onClose) { this.onClose = onClose; }
    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    /** Mode ajout : formulaire vide, titre "Nouvel Investissement", bouton "Ajouter". */
    public void initForAdd() {
        editingInvestment = null;
        if (dialogTitle != null) dialogTitle.setText("Nouvel Investissement");
        if (submitButton != null) submitButton.setText("Ajouter");
        clearFields();
    }

    /** Mode modification : pré-remplit le formulaire avec l'investissement. */
    public void initForEdit(Investment inv) {
        editingInvestment = inv;
        if (dialogTitle != null) dialogTitle.setText("Modifier l'investissement");
        if (submitButton != null) submitButton.setText("Modifier");
        if (inv == null) {
            clearFields();
            return;
        }
        commentaireField.setText(inv.getCommentaireInv() == null ? "" : inv.getCommentaireInv());
        dureeField.setText(inv.getDureeInv() == null ? "" : inv.getDureeInv().toString());
        budMinField.setText(String.valueOf(inv.getBud_minInv()));
        budMaxField.setText(String.valueOf(inv.getBud_maxInv()));
        if (inv.getCurrencyInv() != null && currencyCombo.getItems().contains(inv.getCurrencyInv())) {
            currencyCombo.getSelectionModel().select(inv.getCurrencyInv());
        } else {
            currencyCombo.getSelectionModel().select("TND");
        }
        idProjField.setText(String.valueOf(inv.getIdProj()));
        idUserField.setText(String.valueOf(inv.getIdUser()));
    }

    private void clearFields() {
        if (commentaireField != null) commentaireField.clear();
        if (dureeField != null) dureeField.clear();
        if (budMinField != null) budMinField.clear();
        if (budMaxField != null) budMaxField.clear();
        if (currencyCombo != null) currencyCombo.getSelectionModel().select("TND");
        if (idProjField != null) idProjField.clear();
        if (idUserField != null) idUserField.clear();
    }

    @FXML
    private void close() {
        onClose.run();
    }

    @FXML
    private void save() {
        String commentaire = commentaireField == null ? "" : commentaireField.getText();
        String dureeStr = dureeField == null ? "00:00:00" : dureeField.getText().trim();
        if (dureeStr.isEmpty()) dureeStr = "00:00:00";
        Time duree;
        try {
            duree = Time.valueOf(dureeStr);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Durée invalide. Utilisez HH:mm:ss").showAndWait();
            return;
        }
        double budMin, budMax;
        try {
            budMin = Double.parseDouble(budMinField.getText().trim());
            budMax = Double.parseDouble(budMaxField.getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Budget min et max doivent être des nombres.").showAndWait();
            return;
        }
        String currency = currencyCombo.getValue() == null ? "TND" : currencyCombo.getValue();
        int idProj, idUser;
        try {
            idProj = Integer.parseInt(idProjField.getText().trim());
            idUser = Integer.parseInt(idUserField.getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "ID Projet et ID Utilisateur doivent être des entiers.").showAndWait();
            return;
        }

        if (editingInvestment != null) {
            editingInvestment.setCommentaireInv(commentaire);
            editingInvestment.setDureeInv(duree);
            editingInvestment.setBud_minInv(budMin);
            editingInvestment.setBud_maxInv(budMax);
            editingInvestment.setCurrencyInv(currency);
            editingInvestment.setIdProj(idProj);
            editingInvestment.setIdUser(idUser);
            service.modifier(editingInvestment);
        } else {
            Investment inv = new Investment(commentaire, duree, budMin, budMax, currency, idProj, idUser);
            service.ajouter(inv);
        }
        onSaved.run();
    }
}
