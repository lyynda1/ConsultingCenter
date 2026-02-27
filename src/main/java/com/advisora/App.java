package com.advisora;

import com.advisora.Services.strategie.RiskContext;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        // ✅ Start external risk monitor once
        RiskContext.init();

        String startView = "/GUI/Auth/login.fxml";
        FXMLLoader loader = new FXMLLoader(getClass().getResource(startView));
        Parent root = loader.load();
        Scene scene = new Scene(root);

        primaryStage.setTitle("Advisora - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        // ✅ stop scheduler thread on app close
        RiskContext.shutdown();
    }
}