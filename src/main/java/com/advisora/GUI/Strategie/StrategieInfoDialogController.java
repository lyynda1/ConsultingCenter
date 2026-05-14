package com.advisora.GUI.Strategie;


import com.advisora.Model.strategie.Objective;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.strategie.ServiceObjective;
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
    @FXML private Label lblProjet;
    @FXML private Label lblCreatedAt;
    @FXML private Label lblType;
    @FXML private Label lblStatut;
    @FXML private Label lblBudget;
    @FXML private Label lblGain;
    @FXML private Label lblRoi;
    @FXML private Label lblJustification;
    @FXML private Label lblTerm;





    @FXML private HBox recommendActionsBox;

    private Runnable onConfirm;
    private Runnable onIgnore;

    public void setOnConfirm(Runnable r) { this.onConfirm = r; }
    public void setOnIgnore(Runnable r) { this.onIgnore = r; }

    @FXML private void onConfirm() { if (onConfirm != null) onConfirm.run(); }
    @FXML private void onIgnore() { if (onIgnore != null) onIgnore.run(); }

    // ✅ call this to enable confirm/ignore mode
    public void enableRecommendationActions(boolean enable) {
        if (recommendActionsBox != null) {
            recommendActionsBox.setVisible(enable);
            recommendActionsBox.setManaged(enable);
        }
    }


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

        lblProjet.setText(s.getProjet() == null ? "-" : s.getProjet().getTitleProj());
        lblCreatedAt.setText(s.getCreatedAt() == null ? "-" : s.getCreatedAt().toLocalDate().toString());
        lblType.setText(s.getTypeStrategie() == null ? "-" : s.getTypeStrategie().name());

        // status badge styling
        String st = (s.getStatut() == null) ? "-" : s.getStatut().toDb();
        lblStatut.setText(st);
        lblStatut.getStyleClass().removeAll("status-pending", "status-accepted", "status-refused", "status-archived");
        if (s.getStatut() != null) {
            switch (s.getStatut()) {
                case EN_COURS -> lblStatut.getStyleClass().add("status-pending");
                case ACCEPTEE -> lblStatut.getStyleClass().add("status-accepted");
                case REFUSEE -> lblStatut.getStyleClass().add("status-refused");
                case NON_AFFECTEE -> lblStatut.getStyleClass().add("status-pending");

                default -> lblStatut.getStyleClass().add("status-archived");
            }
        }

        // money values (avoid null/0 issues)
        Double budget = s.getBudgetTotal();
        Double gain = s.getGainEstime();

        lblBudget.setText(budget == null ? "-" : String.format("%,.0f $", budget));
        lblGain.setText(gain == null ? "-" : String.format("%,.0f $", gain));

        if (budget != null && gain != null && budget > 0) {
            double roi = (gain - budget) / budget; // example formula
            lblRoi.setText("ROI : " + String.format("%.0f%%", roi * 100));
        } else {
            lblRoi.setText("ROI : -");
        }

        lblTerm.setText(s.getDureeTerme() == null ? "-" : s.getDureeTerme() + " jours");

        // justification
        String j = s.getJustification();
        boolean hasJ = j != null && !j.trim().isEmpty();
        lblJustification.setText(hasJ ? "Justification : " + j.trim() : "");
        lblJustification.setVisible(hasJ);
        lblJustification.setManaged(hasJ);

        // objectives
        List<Objective> objectives = objectiveService.getByStrategieId(s.getId());
        lblObjectiveCount.setText(String.valueOf(objectives.size()));
        listObjectives.getItems().setAll(objectives);
    }


    @FXML
    private void close() {
        onClose.run();
    }
}
