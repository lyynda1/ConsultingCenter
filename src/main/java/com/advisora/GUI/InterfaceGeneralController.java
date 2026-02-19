package com.advisora.GUI;

import com.advisora.Services.SessionContext;
import com.advisora.enums.UserRole;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class InterfaceGeneralController {
    @FXML private StackPane contentHost;

    @FXML
    private void handleOpenHome() {
        if (contentHost != null) {
            contentHost.getChildren().clear();
        }
    }

    @FXML
    private void handleOpenUsers() {
        showInfo("Gestion Utilisateurs", "Cette vue est geree dans admin.fxml.");
    }

    @FXML
    private void handleOpenProjects() {
        try {
            loadIntoContent("/views/project/ProjectList.fxml");
        } catch (Exception ex) {
            showError("Gestion Projets", ex.getMessage());
        }
    }

    @FXML
    private void handleOpenStrategies() {
        try {
            UserRole r = SessionContext.getCurrentRole();
            if (r != UserRole.ADMIN && r != UserRole.GERANT) {
                showError("Gestion Strategies", "Acces refuse: GERANT ou ADMIN requis.");
                return;
            }
            loadIntoContent("/views/strategie/interfaceStrategie.fxml");
        } catch (Exception ex) {
            showError("Gestion Strategies", ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        handleOpenHome();
    }

    @FXML
    private void handleLogout() {
        try {
            SessionContext.clear();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Auth/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) contentHost.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Advisora - Login");
        } catch (Exception ex) {
            showError("Logout", ex.getMessage());
        }
    }

    private void loadIntoContent(String fxmlPath) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();
        contentHost.getChildren().setAll(root);
    }

    private void showInfo(String header, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private void showError(String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }
}
