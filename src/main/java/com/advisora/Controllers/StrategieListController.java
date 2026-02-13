package com.advisora.Controllers;

import com.advisora.Services.ServiceStrategie;
import com.advisora.Model.Strategie;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;

import java.net.URL;
import java.util.ResourceBundle;

public class StrategieListController implements Initializable {
    private final ServiceStrategie serviceStrategie = new ServiceStrategie();

    // Table and columns
    @FXML
    private TableView<Strategie> listeStrat;

    @FXML
    private TableColumn<Strategie, String> colNom;

    @FXML
    private TableColumn<Strategie, String> colObjectif;

    @FXML
    private TableColumn<Strategie, String> colProjet;

    @FXML
    private TableColumn<Strategie, String> colStatut;

    @FXML
    private TableColumn<Strategie, String> colDate;

    @FXML
    private TableColumn<Strategie, String> colActions;

    // Stat labels
    @FXML
    private Label lblTotalStrategies;

    @FXML
    private Label lblPending;

    @FXML
    private Label lblSuccess;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Configure column cell value factories
        colNom.setCellValueFactory(new PropertyValueFactory<>("nomStrategie"));
        colObjectif.setCellValueFactory(new PropertyValueFactory<>("news"));

        // For colProjet - map to project if available, otherwise empty
        colProjet.setCellValueFactory(cd -> new SimpleStringProperty(""));

        // For colStatut - convert enum to String
        colStatut.setCellValueFactory(cellData -> {
            Strategie s = cellData.getValue();
            String val = (s != null && s.getStatut() != null) ? s.getStatut().name() : "";
            return new SimpleStringProperty(val);
        });

        // For colDate - format LocalDateTime as String
        colDate.setCellValueFactory(cellData -> {
            Strategie s = cellData.getValue();
            String val = (s != null && s.getCreatedAt() != null) ? s.getCreatedAt().toString() : "";
            return new SimpleStringProperty(val);
        });

        // For colActions - placeholder (will add edit/delete buttons later)
        colActions.setCellValueFactory(cd -> new SimpleStringProperty("Edit | Delete"));

        // Load data from service
        loadStrategies();

        // Update stat labels
        updateStatistics();
    }

    private void loadStrategies() {
        ObservableList<Strategie> data = FXCollections.observableArrayList();
        try {
            data.addAll(serviceStrategie.afficher());
        } catch (Exception e) {
            System.err.println("Failed to load strategies: " + e.getMessage());
        }
        listeStrat.setItems(data);
    }

    private void updateStatistics() {
        try {
            java.util.List<Strategie> all = serviceStrategie.afficher();
            lblTotalStrategies.setText(String.valueOf(all.size()));

            // Count pending (assuming those with status that need approval)
            long pending = all.stream()
                    .filter(s -> s.getStatut() != null && s.getStatut().name().contains("Pending"))
                    .count();
            lblPending.setText(String.valueOf(pending));

            // Calculate success rate (placeholder logic)
            lblSuccess.setText("0%");
        } catch (Exception e) {
            System.err.println("Failed to update statistics: " + e.getMessage());
        }
    }

    @FXML
    void nouvelleStrategie(ActionEvent event) {
        System.out.println("Nouvelle Stratégie clicked");
        // TODO: Open dialog to create new strategy
    }

    // Sidebar button handlers (for navigation)
    @FXML
    void projet(ActionEvent event) {
        System.out.println("Projets clicked");
        // TODO: Navigate to project interface
    }

    @FXML
    void strategie(ActionEvent event) {
        System.out.println("Stratégies clicked");
        // TODO: Reload or stay on this screen
    }

    @FXML
    void ressource(ActionEvent event) {
        System.out.println("Ressource clicked");
        // TODO: Navigate to resource interface
    }

    @FXML
    void event(ActionEvent event) {
        System.out.println("Evenements clicked");
        // TODO: Navigate to event interface
    }
}

