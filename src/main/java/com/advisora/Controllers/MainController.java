package com.advisora.Controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

public class MainController {

    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        // This method is called automatically after the FXML is loaded
        statusLabel.setText("Application started successfully");
    }

    @FXML
    private void handleNewStrategy() {
        showInfo("New Strategy", "Opening new strategy form...");
        // TODO: Open strategy creation form
    }

    @FXML
    private void handleViewStrategies() {
        showInfo("View Strategies", "Loading strategies list...");
        // TODO: Open strategies list view
    }

    @FXML
    private void handleViewUsers() {
        showInfo("View Users", "Loading users list...");
        // TODO: Open users list view
    }

    @FXML
    private void handleAbout() {
        showInfo("About Advisora",
                "Advisora - Strategy Management System\n" +
                "Version 1.0\n\n" +
                "A comprehensive solution for managing business strategies.");
    }

    @FXML
    private void handleExit() {
        System.exit(0);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();

        statusLabel.setText(title);
    }
}
