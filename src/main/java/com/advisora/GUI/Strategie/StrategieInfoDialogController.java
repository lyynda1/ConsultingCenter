package com.advisora.GUI.Strategie;


import com.advisora.Model.Objective;
import com.advisora.Model.Strategie;
import com.advisora.Services.ServiceObjective;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
public class StrategieInfoDialogController {

    @FXML private HBox dragHandle;
    @FXML private Label lblStrategieName;
    @FXML private Label lblObjectiveCount;
    @FXML private ListView<Objective> listObjectives;
    @FXML private Button btnClose;

    private final ServiceObjective objectiveService = new ServiceObjective();

    private Runnable onClose = () -> {};

    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable r) { this.onClose = (r == null ? () -> {} : r); }

    @FXML
    private void initialize() {
        listObjectives.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Objective o, boolean empty) {
                super.updateItem(o, empty);
                if (empty || o == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label title = new Label(o.getNomObjective());
                title.getStyleClass().add("objective-title");

                Label pr = new Label("Priorité: " + o.getPriority());
                pr.getStyleClass().add("pill-priority");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox top = new HBox(10, title, spacer, pr);

                Label desc = new Label(o.getDescription());
                desc.setWrapText(true);
                desc.getStyleClass().add("objective-desc");

                VBox card = new VBox(8, top, desc);
                card.getStyleClass().add("objective-card");

                setText(null);
                setGraphic(card);
            }
        });

    }

    public void initWithStrategie(Strategie s) {
        if (s == null) return;

        lblStrategieName.setText(s.getNomStrategie() == null ? "-" : s.getNomStrategie());

        List<Objective> objectives = objectiveService.getByStrategieId(s.getId());
        lblObjectiveCount.setText(String.valueOf(objectives.size()));
        listObjectives.getItems().setAll(objectives);
    }

    @FXML
    private void close() {
        onClose.run();
    }
}
