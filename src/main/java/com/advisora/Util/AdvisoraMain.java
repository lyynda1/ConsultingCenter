package com.advisora.Util;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AdvisoraMain extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        var url = AdvisoraMain.class.getResource("/GUI/Admin/admin.fxml");
        System.out.println("FXML URL = " + url);
        if (url == null) throw new RuntimeException("FXML not found!");

        FXMLLoader loader = new FXMLLoader(url);
        Scene scene = new Scene(loader.load());
        stage.setTitle("Advisora");
        stage.setScene(scene);
        stage.show();
    }}