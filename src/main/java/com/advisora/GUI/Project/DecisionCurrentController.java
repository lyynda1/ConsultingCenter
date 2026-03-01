package com.advisora.GUI.Project;

import com.advisora.Model.projet.Decision;
import com.advisora.Model.projet.Project;
import com.advisora.Services.projet.DecisionService;
import com.advisora.enums.DecisionStatus;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class DecisionCurrentController {

    @FXML
    private Label lblProjectTitle;
    @FXML
    private Label lblCurrentStatus;
    @FXML
    private Label lblCurrentDate;
    @FXML
    private Label lblCurrentDescription;
    @FXML
    private VBox historySection;
    @FXML
    private ListView<Decision> historyList;
    @FXML
    private Button btnHistory;
    @FXML
    private Label lblHistoryHint;

    private final DecisionService decisionService = new DecisionService();
    private Project currentProject;
    private boolean historyLoaded;

    public void initWithProject(Project project) {
        this.currentProject = project;
        lblProjectTitle.setText(project.getTitleProj() == null ? ("Projet #" + project.getIdProj()) : project.getTitleProj());
        loadCurrentDecision();
        historySection.setVisible(false);
        historySection.setManaged(false);
        btnHistory.setText("Voir historique");
        lblHistoryHint.setText("Cliquez sur \"Voir historique\" pour afficher les decisions de ce projet.");
    }

    private void loadCurrentDecision() {
        List<Decision> decisions = decisionService.getByProject(currentProject.getIdProj());
        Decision current = decisions.stream()
                .filter(d -> d.getStatutD() != DecisionStatus.PENDING)
                .findFirst()
                .orElse(null);

        if (current == null) {
            lblCurrentStatus.setText("Aucune decision actuelle");
            lblCurrentDate.setText("-");
            lblCurrentDescription.setText("Aucune decision valide pour ce projet.");
            return;
        }

        lblCurrentStatus.setText(String.valueOf(current.getStatutD()));
        lblCurrentDate.setText(current.getDateDecision() == null
                ? "-"
                : current.getDateDecision().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        lblCurrentDescription.setText(current.getDescriptionD() == null ? "-" : current.getDescriptionD());
    }

    private void loadHistory() {
        List<Decision> decisions = decisionService.getByProject(currentProject.getIdProj());
        historyList.setItems(FXCollections.observableArrayList(decisions));
        historyList.setPlaceholder(new Label("Aucune decision enregistree pour ce projet."));
        historyList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Decision item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String date = item.getDateDecision() == null
                        ? "-"
                        : item.getDateDecision().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                setText(item.getStatutD() + " | " + date + " | " + (item.getDescriptionD() == null ? "-" : item.getDescriptionD()));
            }
        });
        historyLoaded = true;
    }

    @FXML
    private void onToggleHistory() {
        boolean visible = !historySection.isVisible();
        if (visible && !historyLoaded) {
            loadHistory();
        }
        historySection.setVisible(visible);
        historySection.setManaged(visible);
        btnHistory.setText(visible ? "Masquer historique" : "Voir historique");
        lblHistoryHint.setText(visible
                ? "Historique affiche ci-dessous."
                : "Cliquez sur \"Voir historique\" pour afficher les decisions de ce projet.");
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) lblProjectTitle.getScene().getWindow();
        stage.close();
    }
}

