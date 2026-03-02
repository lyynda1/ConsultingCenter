package com.advisora.GUI.Objective;

import com.advisora.Model.strategie.Objective;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.strategie.ServiceObjective;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
    private Strategie strategie;
    private Objective editing;

    @FXML
    public void initialize() {
        if (dialogTitle != null) {
            dialogTitle.setText("Ajouter un objectif");
        }
        if (saveBtn != null) {
            saveBtn.setText("Ajouter");
        }
    }

    public Node getDragHandle() {
        return dragHandle;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setStrategie(Strategie strategie) {
        this.strategie = strategie;
    }

    public void setEditingObjective(Objective objective) {
        this.editing = objective;
        if (objective == null) {
            resetForm();
            return;
        }
        if (dialogTitle != null) {
            dialogTitle.setText("Modifier l'objectif");
        }
        if (saveBtn != null) {
            saveBtn.setText("Enregistrer");
        }
        nomObjectiveField.setText(objective.getNomObjective() == null ? "" : objective.getNomObjective());
        descriptionField.setText(objective.getDescription() == null ? "" : objective.getDescription());
        priorityField.setText(String.valueOf(objective.getPriority()));
    }

    @FXML
    private void save() {
        String nomObjective = value(nomObjectiveField.getText());
        String description = value(descriptionField.getText());
        String priorityText = value(priorityField.getText());

        if (strategie == null || strategie.getId() <= 0) {
            showAlert(Alert.AlertType.ERROR, "Aucune strategie selectionnee.");
            return;
        }
        if (nomObjective.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Nom obligatoire.");
            return;
        }
        if (description.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Description obligatoire.");
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(priorityText);
            if (priority < 0) {
                showAlert(Alert.AlertType.WARNING, "Priorite doit etre >= 0.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Priorite invalide (entier).");
            return;
        }

        try {
            if (editing == null) {
                Objective obj = new Objective(strategie.getId(), nomObjective, description, priority);
                service.ajouter(obj);
            } else {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirmation");
                confirm.setHeaderText("Confirmer la modification ?");
                confirm.setContentText("Objectif: " + editing.getNomObjective());
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
                editing.setStrategieId(strategie.getId());
                editing.setNomObjective(nomObjective);
                editing.setDescription(description);
                editing.setPriority(priority);
                service.modifier(editing);
            }
            onSaved.run();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur: " + e.getMessage());
        }
    }

    @FXML
    private void close() {
        onClose.run();
    }

    public void resetForm() {
        editing = null;
        nomObjectiveField.clear();
        priorityField.clear();
        descriptionField.clear();
        if (dialogTitle != null) {
            dialogTitle.setText("Ajouter un objectif");
        }
        if (saveBtn != null) {
            saveBtn.setText("Ajouter");
        }
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.showAndWait();
    }
}

