/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Application bootstrap/entrypoint
*/

package com.advisora;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class App extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            // Load the login FXML file
            URL fxmlResource = getClass().getResource("/GUI/Auth/login.fxml");

            if (fxmlResource == null) {
                System.err.println("ERROR: Cannot find login.fxml at /GUI/Auth/login.fxml");
                System.err.println("Available resources:");
                System.err.println("  - Checked path: " + getClass().getResource("/"));
                throw new RuntimeException("login.fxml not found in resources");
            }

            FXMLLoader loader = new FXMLLoader(fxmlResource);
            Parent root = loader.load();
            Scene scene = new Scene(root);

            primaryStage.setTitle("Advisora - Login");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1600);
            primaryStage.setMinHeight(1000);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: Failed to load application");
            e.printStackTrace();
            throw e;
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting Advisora Application...");
        try {
            launch(args);
        } catch (Exception e) {
            System.err.println("FATAL: Failed to launch JavaFX application");
            e.printStackTrace();
            System.exit(1);
        }
    }
}

