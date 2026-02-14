package com.advisora.GUI.Objective;

import com.advisora.Model.Objective;
import com.advisora.Model.Strategie;
import com.advisora.Services.ServiceObjective;   // ✅ you need this service
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public class AddObjectifController {

    @FXML private TextField nomObjectiveField;
    @FXML private TextField priorityField;
    @FXML private TextArea descriptionField;

    @FXML private HBox dragHandle;
    @FXML private Label dialogTitle;
    @FXML private Button saveBtn;

    private final ServiceObjective service = new ServiceObjective();

    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};

    private Strategie strategie;        // ✅ objective belongs to this strategy
    private Objective editing;          // null => add, not null => edit

    @FXML
    public void initialize() {
        if (dialogTitle != null) dialogTitle.setText("Ajouter un objectif");
        if (saveBtn != null) saveBtn.setText("Ajouter");
    }

    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable r) { this.onClose = r; }
    public void setOnSaved(Runnable r) { this.onSaved = r; }

    /** Call this when opening dialog from a selected Strategie */
    public void setStrategie(Strategie s) {
        this.strategie = s;
    }

    /** Call this when editing an existing objective */
    public void setEditingObjective(Objective obj) {
        this.editing = obj;

        if (dialogTitle != null) dialogTitle.setText("Modifier l'objectif");
        if (saveBtn != null) saveBtn.setText("Enregistrer");

        descriptionField.setText(obj.getDescription() == null ? "" : obj.getDescription());
        priorityField.setText(String.valueOf(obj.getPriority()));
        nomObjectiveField.setText(obj.getNomObjective() == null ? "" : obj.getNomObjective());
    }

    @FXML
    private void close() {
        onClose.run();
    }

    @FXML
    private void save() {
        String nomObjective = nomObjectiveField.getText() == null ? "" : nomObjectiveField.getText().trim();
        String desc = descriptionField.getText() == null ? "" : descriptionField.getText().trim();
        String prTxt = priorityField.getText() == null ? "" : priorityField.getText().trim();

        if (strategie == null) {
            new Alert(Alert.AlertType.ERROR, "Aucune stratégie sélectionnée.").showAndWait();
            return;
        }
        if (strategie.getId() <= 0) {
            new Alert(Alert.AlertType.ERROR, "ID stratégie invalide.").showAndWait();
            return;
        }
        if (desc.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Description obligatoire.").showAndWait();
            return;
        }
        if (nomObjective.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Nom obligatoire.").showAndWait();
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(prTxt);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.WARNING, "Priorité invalide (entier).").showAndWait();
            return;
        }

        try {
            if (editing == null) {
                // ✅ ADD
                Objective obj = new Objective(strategie.getId(), nomObjective, desc, priority);
                service.ajouter(obj);
            } else {
                // ✅ CONFIRM EDIT
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirmation");
                confirm.setHeaderText("Confirmer la modification ?");
                confirm.setContentText("Objectif: " + editing.getNomObjective());

                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

                editing.setStrategieId(strategie.getId());
                editing.setNomObjective(nomObjective);
                editing.setDescription(desc);
                editing.setPriority(priority);
                service.modifier(editing);
            }

            onSaved.run();

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Erreur: " + ex.getMessage()).showAndWait();
        }
    }

    public void resetForm() {

        nomObjectiveField.clear();
        priorityField.clear();
        descriptionField.clear();
        if (dialogTitle != null) dialogTitle.setText("Ajouter un objectif");
        if (saveBtn != null) saveBtn.setText("Ajouter");
        editing = null;


    }
}
