package com.advisora.GUI.Strategie;

import com.advisora.Model.Strategie;
import com.advisora.Services.ServiceStrategie;
import com.advisora.enums.StrategyStatut;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.time.LocalDateTime;
import java.util.function.Consumer;

public class AddStrategieDialogController {

    @FXML private TextField nomField;
    @FXML private TextArea objectifField;
    @FXML private ComboBox<StrategyStatut> statutCombo;
    @FXML private TextField versionField;

    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};

    private final ServiceStrategie service = new ServiceStrategie();
    @FXML private HBox dragHandle;

    @FXML
    public void initialize() {
        statutCombo.getItems().setAll(StrategyStatut.values());
        statutCombo.getSelectionModel().selectFirst();
    }

    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable onClose) { this.onClose = onClose; }
    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    @FXML
    private void close() {
        onClose.run();
    }

    @FXML
    private void save() {
        Strategie s = new Strategie(
                nomField.getText(),
                Double.parseDouble(versionField.getText()),
                statutCombo.getValue(),
                LocalDateTime.now(),
                null,
                null,                      // news

                null                       // projet
        );

        service.ajouter(s);
        onSaved.run();
    }
}
