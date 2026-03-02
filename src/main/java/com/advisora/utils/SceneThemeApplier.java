package com.advisora.utils;


import com.advisora.accessibility.VoiceAssistantManager;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SceneThemeApplier {
    private SceneThemeApplier() {
    }

    public static Scene apply(Scene scene) {
        ThemeManager.applySavedTheme(scene);
        VoiceAssistantManager.applyToScene(scene);
        return scene;
    }

    public static Scene createScene(Parent root) {
        return apply(new Scene(root));
    }

    public static Scene createScene(Parent root, double width, double height) {
        return apply(new Scene(root, width, height));
    }

    public static void setScene(Stage stage, Parent root) {
        stage.setScene(createScene(root));
    }

    public static void setScene(Stage stage, Parent root, double width, double height) {
        stage.setScene(createScene(root, width, height));
    }

    public static void setScene(Stage stage, Scene scene) {
        stage.setScene(apply(scene));
    }
}


