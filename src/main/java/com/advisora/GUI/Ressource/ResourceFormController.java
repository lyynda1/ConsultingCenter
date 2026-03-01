package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Model.ressource.Ressource;
import com.advisora.Services.ressource.CatalogueFournisseurService;
import com.advisora.Services.ressource.RessourceService;
import com.advisora.enums.RessourceStatut;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class ResourceFormController {
    @FXML private ComboBox<CatalogueFournisseur> comboSupplier;
    @FXML private TextField txtName;
    @FXML private TextField txtPrice;
    @FXML private TextField txtQty;
    @FXML private ComboBox<RessourceStatut> comboStatus;
    @FXML private Button btnDelete;

    private final CatalogueFournisseurService fournisseurService = new CatalogueFournisseurService();
    private final RessourceService service = new RessourceService();

    private Ressource current;
    private boolean editMode;

    @FXML
    public void initialize() {
        comboStatus.setItems(FXCollections.observableArrayList(RessourceStatut.values()));
        comboStatus.setValue(RessourceStatut.AVAILABLE);
        comboSupplier.setItems(FXCollections.observableArrayList(fournisseurService.afficher()));
        if (!comboSupplier.getItems().isEmpty()) {
            comboSupplier.getSelectionModel().selectFirst();
        }
    }

    public void initForCreate(int supplierId) {
        this.editMode = false;
        this.current = null;
        btnDelete.setVisible(false);
        btnDelete.setManaged(false);
        if (supplierId > 0) {
            for (CatalogueFournisseur c : comboSupplier.getItems()) {
                if (c.getIdFr() == supplierId) {
                    comboSupplier.setValue(c);
                    break;
                }
            }
        }
    }

    public void initForEdit(Ressource ressource) {
        this.editMode = true;
        this.current = ressource;
        btnDelete.setVisible(true);
        btnDelete.setManaged(true);

        txtName.setText(ressource.getNomRs());
        txtPrice.setText(String.valueOf(ressource.getPrixRs()));
        txtQty.setText(String.valueOf(ressource.getQuantiteRs()));
        comboStatus.setValue(ressource.getAvailabilityStatusRs());

        for (CatalogueFournisseur c : comboSupplier.getItems()) {
            if (c.getIdFr() == ressource.getIdFr()) {
                comboSupplier.setValue(c);
                break;
            }
        }
    }

    @FXML
    private void onSave() {
        try {
            Ressource r = current == null ? new Ressource() : current;
            CatalogueFournisseur supplier = comboSupplier.getValue();
            if (supplier == null) {
                throw new IllegalArgumentException("Aucun fournisseur selectionne. Ajoutez d'abord un fournisseur.");
            }
            r.setIdFr(supplier.getIdFr());
            r.setNomRs(required(txtName.getText(), "Nom obligatoire"));
            r.setPrixRs(parseDouble(txtPrice.getText(), "Prix"));
            r.setQuantiteRs(parseInt(txtQty.getText(), "Quantite"));
            RessourceStatut statut = comboStatus.getValue();
            if (statut == null) {
                statut = r.getQuantiteRs() > 0 ? RessourceStatut.AVAILABLE : RessourceStatut.UNAVAILABLE;
            }
            r.setAvailabilityStatusRs(statut);

            if (editMode) {
                service.modifier(r);
            } else {
                service.ajouter(r);
            }
            close();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onDelete() {
        clearForm();
    }

    private void clearForm() {
        txtName.clear();
        txtPrice.clear();
        txtQty.clear();
        comboStatus.setValue(RessourceStatut.AVAILABLE);
        comboSupplier.getSelectionModel().clearSelection();
    }

    private String required(String value, String msg) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
        return value.trim();
    }

    private int parseInt(String value, String field) {
        try {
            int v = Integer.parseInt(required(value, field + " obligatoire").replace(" ", ""));
            if (v < 0) {
                throw new IllegalArgumentException(field + " >= 0");
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " doit etre un nombre");
        }
    }

    private double parseDouble(String value, String field) {
        try {
            String normalized = required(value, field + " obligatoire").replace(" ", "").replace(",", ".");
            double v = Double.parseDouble(normalized);
            if (v < 0) {
                throw new IllegalArgumentException(field + " >= 0");
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " doit etre un nombre");
        }
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Form ressource");
        a.showAndWait();
    }

    private void close() {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.close();
    }
}

