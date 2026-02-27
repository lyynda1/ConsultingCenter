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

public class App extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        String startView = "/GUI/Auth/login.fxml";
        FXMLLoader loader = new FXMLLoader(getClass().getResource(startView));
        Parent root = loader.load();
        Scene scene = new Scene(root);
        primaryStage.setTitle("Advisora - Login");

        primaryStage.setScene(scene);

        primaryStage.show();
    }
}
