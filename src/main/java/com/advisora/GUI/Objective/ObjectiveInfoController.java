package com.advisora.GUI.Objective;

import com.advisora.Model.Objective;
import com.advisora.Services.ServiceObjective;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public class ObjectiveInfoController {

    @FXML private Label nomLabel;
    @FXML private TextArea descArea;
    @FXML private Label priorityLabel;
    @FXML private HBox dragHandle;

    private final ServiceObjective service = new ServiceObjective();
    private Objective objective;

    private Runnable onClose = () -> {};
    private Runnable onRefresh = () -> {};

    public Node getDragHandle() { return dragHandle; }

    public void setObjective(Objective obj) {
        this.objective = obj;

        nomLabel.setText("Nom: " + obj.getNomObjective());
        descArea.setText("Description: " + obj.getDescription());
        priorityLabel.setText("Priorité: " + obj.getPriority());
    }

    public void setOnClose(Runnable r) { this.onClose = r; }
    public void setOnRefresh(Runnable r) { this.onRefresh = r; }

    @FXML
    private void delete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Supprimer cet objectif ?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        service.supprimer(objective);
        onRefresh.run();
        onClose.run();
    }

    @FXML
    private void modify() {
        onClose.run();
        // reopen AddObjectifController in edit mode
        // we’ll connect this in next step
    }

    public void close(ActionEvent actionEvent) {
        onClose.run();
    }
}
