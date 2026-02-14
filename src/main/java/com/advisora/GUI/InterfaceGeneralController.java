package com.advisora.GUI;

import com.advisora.Services.SessionContext;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class InterfaceGeneralController {

    @FXML private Button strategiesBtn;
    @FXML private Label roleLabel;
    @FXML private StackPane contentHost;
    @FXML private Label leftStatus;

    @FXML
    public void initialize() {
        boolean canSeeStrategies = SessionContext.isGerant() || SessionContext.isAdmin();
        if (strategiesBtn != null) {
            strategiesBtn.setVisible(canSeeStrategies);
            strategiesBtn.setManaged(canSeeStrategies);
        }
        if (roleLabel != null && SessionContext.getCurrentRole() != null) {
            roleLabel.setText(SessionContext.getCurrentRole().name());
        }

        // ✅ DO NOT load InterfaceGeneral into contentHost
        // ✅ Home is already displayed in the center by default
    }

    @FXML
    private void handleOpenHome(ActionEvent e) {
        // ✅ go back to the shell by reloading the whole scene
        reloadShell();
    }

    @FXML
    private void handleOpenStrategies() {
        loadIntoContentHost("/views/strategie/interfaceStrategie.fxml"); // must be content-only
    }

    @FXML
    private void handleOpenProjects() {
        loadIntoContentHost("/views/project/ProjectList.fxml"); // must be content-only
    }

    @FXML
    private void handleOpenUsers() {
        // If admin.fxml is full page => open as scene
        loadIntoContentHost("/GUI/Admin/admin.fxml");
    }




    private void loadIntoContentHost(String fxmlPath) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentHost.getChildren().setAll(view);
        } catch (Exception ex) {
            ex.printStackTrace();
            if (leftStatus != null) leftStatus.setText("Navigation failed ❌ " + ex.getMessage());
        }
    }

    private void reloadShell() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/InterfaceGeneral.fxml"));
            Stage stage = (Stage) contentHost.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception ex) {
            ex.printStackTrace();
            if (leftStatus != null) leftStatus.setText("Home failed ❌ " + ex.getMessage());
        }
    }

    private void openAsScene(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) contentHost.getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void handleRefresh(ActionEvent actionEvent) {
        // optional
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
            leftStatus.setText("Logout failed ❌ " + ex.getMessage());
        }
    }
}
