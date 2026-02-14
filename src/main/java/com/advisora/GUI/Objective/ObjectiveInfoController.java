package com.advisora.GUI.Objective;

import com.advisora.Model.Objective;
import com.advisora.Services.ServiceObjective;
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

    // ✅ NEW: ask parent to open edit dialog
    private java.util.function.Consumer<Objective> onEditRequested = o -> {};

    public Node getDragHandle() { return dragHandle; }

    public void setObjective(Objective obj) {
        this.objective = obj;
        nomLabel.setText("Nom: " + obj.getNomObjective());
        descArea.setText(obj.getDescription());          // no "Description:" prefix inside textarea
        priorityLabel.setText("Priorité: " + obj.getPriority());
    }

    public void setOnClose(Runnable r) { this.onClose = r; }
    public void setOnRefresh(Runnable r) { this.onRefresh = r; }

    public void setOnEditRequested(java.util.function.Consumer<Objective> c) {
        this.onEditRequested = (c == null) ? (o -> {}) : c;
    }

    @FXML
    private void delete() {
        if (objective == null || objective.getId() <= 0) {
            new Alert(Alert.AlertType.ERROR, "Objectif invalide (ID).").showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cet objectif ?");
        confirm.setContentText(objective.getNomObjective());

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            service.supprimer(objective);
            onRefresh.run();
            onClose.run();
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Suppression échouée: " + ex.getMessage()).showAndWait();
        }
    }

    @FXML
    private void modify() {
        if (objective == null) return;

        // ✅ close info dialog then ask parent to open edit dialog
        onClose.run();
        onEditRequested.accept(objective);
    }

    @FXML
    private void close() {
        onClose.run();
    }
}
