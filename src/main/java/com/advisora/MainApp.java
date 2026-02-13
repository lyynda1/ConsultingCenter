package com.advisora;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the FXML file
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        // Set up the scene
        Scene scene = new Scene(root, 800, 600);

        // Optional: Add CSS stylesheet
        // scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        // Set up the stage
        primaryStage.setTitle("Advisora - Strategy Management");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
