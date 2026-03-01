package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Services.ressource.CatalogueFournisseurService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class SupplierFormController {
    @FXML private TextField txtName;
    @FXML private TextField txtQuantite;
    @FXML private TextField txtFournisseur;
    @FXML private TextField txtEmail;
    @FXML private TextField txtLocalisation;
    @FXML private TextField txtNumTel;
    @FXML private Button btnDelete;

    private final CatalogueFournisseurService service = new CatalogueFournisseurService();
    private CatalogueFournisseur current;
    private boolean editMode;

    public void initForCreate() {
        this.editMode = false;
        this.current = null;
        btnDelete.setVisible(false);
        btnDelete.setManaged(false);
    }

    public void initForEdit(CatalogueFournisseur fournisseur) {
        this.editMode = true;
        this.current = fournisseur;
        btnDelete.setVisible(true);
        btnDelete.setManaged(true);
        txtName.setText(fournisseur.getNomFr());
        txtQuantite.setText(String.valueOf(fournisseur.getQuantite()));
        txtFournisseur.setText(fournisseur.getFournisseur());
        txtEmail.setText(fournisseur.getEmailFr());
        txtLocalisation.setText(fournisseur.getLocalisationFr());
        txtNumTel.setText(fournisseur.getNumTelFr());
    }

    @FXML
    private void onSave() {
        try {
            CatalogueFournisseur f = current == null ? new CatalogueFournisseur() : current;
            f.setNomFr(required(txtName.getText(), "Nom fournisseur obligatoire"));
            f.setQuantite(requiredInt(txtQuantite.getText(), "QuantitÃƒÂ© du produit est obligatoire"));
            f.setFournisseur(required(txtFournisseur.getText(), "entreprise fournisseur obligatoire"));
            f.setEmailFr(requiredEmail(txtEmail.getText(), "Email obligatoire"));
            f.setLocalisationFr(required(txtLocalisation.getText(), "Localisation obligatoire"));
            f.setNumTelFr(requiredTel(txtNumTel.getText(), "NumÃƒÂ©ro de tÃƒÂ©lÃƒÂ©phone obligatoire"));
            if (editMode) {
                service.modifier(f);
            } else {
                service.ajouter(f);
            }
            close();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private String requiredTel(String text, String requiredMessage) {
        String v = required(text, requiredMessage);
        if (!v.matches("^\\+?[0-9]{7,15}$")) {
            throw new IllegalArgumentException("NumÃƒÂ©ro de tÃƒÂ©lÃƒÂ©phone invalide");
        }
        return v;
    }

    private String requiredEmail(String text, String emailObligatoire) {
        String v = required(text, emailObligatoire);
        if (!v.matches("^[\\w.-]+@[\\w.-]+\\.\\w+$")) {
            throw new IllegalArgumentException("Email invalide");
        }
        return v;
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onDelete() {
        txtName.clear();
        txtQuantite.clear();
        txtFournisseur.clear();
        txtEmail.clear();
        txtLocalisation.clear();
        txtNumTel.clear();
    }

    private String required(String value, String msg) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
        return value.trim();
    }

    private int requiredInt(String value, String msg) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
        try {
            int v = Integer.parseInt(value.trim());
            if (v < 0) {
                throw new IllegalArgumentException(msg);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(msg);
        }
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Form fournisseur");
        a.showAndWait();
    }

    private void close() {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.close();
    }
}

