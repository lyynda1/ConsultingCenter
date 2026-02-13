/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Application bootstrap/entrypoint
*/
package com.advisora;

import com.advisora.Services.SessionContext;
import com.advisora.enums.UserRole;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    //CLIENT: Edit, Delete MANAGER: Decide
    @Override
    public void start(Stage primaryStage) throws Exception {
        int userId = Integer.parseInt(System.getProperty("advisora.userId", "4"));
        String roleValue = System.getProperty("advisora.role", "client").trim().toUpperCase();

        UserRole role = UserRole.valueOf(roleValue);
        SessionContext.setCurrentUser(userId, role);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/ProjectList.fxml"));
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(getClass().getResource("/views/style/proj.css").toExternalForm());
        primaryStage.setTitle("Advisora - Projects (" + role + ")");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(900);
        primaryStage.show();
    }
}
