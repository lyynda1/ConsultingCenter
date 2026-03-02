package com.advisora.GUI.Objective;

import com.advisora.Model.strategie.Objective;
import com.advisora.Services.strategie.ServiceObjective;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public class ObjectiveInfoController {
    @FXML private Label nomLabel;
    @FXML private TextArea descArea;
    @FXML private Label priorityLabel;
    @FXML private HBox dragHandle;

    private final ServiceObjective service = new ServiceObjective();
    private Objective objective;
    private Runnable onClose = () -> {};
    private Runnable onRefresh = () -> {};
    private Consumer<Objective> onEditRequested = o -> {};

    public Node getDragHandle() {
        return dragHandle;
    }

    public void setObjective(Objective objective) {
        this.objective = objective;
        nomLabel.setText("Nom: " + (objective == null ? "" : objective.getNomObjective()));
        descArea.setText(objective == null ? "" : objective.getDescription());
        priorityLabel.setText("Priorite: " + (objective == null ? "-" : objective.getPriority()));
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnRefresh(Runnable onRefresh) {
        this.onRefresh = onRefresh;
    }

    public void setOnEditRequested(Consumer<Objective> onEditRequested) {
        this.onEditRequested = onEditRequested == null ? o -> {} : onEditRequested;
    }

    @FXML
    private void delete() {
        if (objective == null || objective.getId() <= 0) {
            showError("Objectif invalide (ID).");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cet objectif ?");
        confirm.setContentText(objective.getNomObjective());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            service.supprimer(objective);
            onRefresh.run();
            onClose.run();
        } catch (Exception e) {
            showError("Suppression echouee: " + e.getMessage());
        }
    }

    @FXML
    private void modify() {
        if (objective == null) {
            return;
        }
        onClose.run();
        onEditRequested.accept(objective);
    }

    @FXML
    private void close() {
        onClose.run();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}

