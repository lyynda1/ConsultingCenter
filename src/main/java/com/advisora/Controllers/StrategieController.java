package com.advisora.Controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;

import java.net.URL;
import java.util.ResourceBundle;

public class StrategieController implements Initializable {

    // Main content area for dynamic navigation
    @FXML
    private AnchorPane contentArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Show home screen by default
        showHome();
    }

    /**
     * Load home screen (default welcome screen)
     */
    private void showHome() {
        // The default content from interface.fxml is already shown
        contentArea.getChildren().clear();
        // Re-add the default home content if needed
        // For now, the default content stays as-is from FXML
    }

    /**
     * Dynamically load FXML file into contentArea
     */
    private void loadFXML(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            AnchorPane newContent = loader.load();

            // Clear current content and add new content
            contentArea.getChildren().clear();
            contentArea.getChildren().add(newContent);

            // Bind new content to fill the contentArea
            AnchorPane.setTopAnchor(newContent, 0.0);
            AnchorPane.setLeftAnchor(newContent, 0.0);
            AnchorPane.setRightAnchor(newContent, 0.0);
            AnchorPane.setBottomAnchor(newContent, 0.0);
        } catch (Exception e) {
            System.err.println("Failed to load FXML: " + fxmlPath);
            e.printStackTrace();
        }
    }

    // ============== Button Handlers ==============

    @FXML
    void projet(ActionEvent event) {
        System.out.println("Projets clicked");
        // TODO: loadFXML("/fxml/interfaceProjet.fxml");
    }

    @FXML
    void strategie(ActionEvent event) {
        System.out.println("Stratégies clicked");
        loadFXML("/fxml/interfaceStrategie.fxml");
    }

    @FXML
    void ressource(ActionEvent event) {
        System.out.println("Ressource clicked");
        // TODO: loadFXML("/fxml/interfaceRessource.fxml");
    }

    @FXML
    void event(ActionEvent event) {
        System.out.println("Evenements clicked");
        // TODO: loadFXML("/fxml/interfaceEvenement.fxml");
    }
}

