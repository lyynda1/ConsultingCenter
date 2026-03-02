package com.advisora.utils.i18n;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.net.URL;

public final class FxLoader {

    private FxLoader() {}

    public static Parent load(String fxmlPath) throws Exception {
        URL url = FxLoader.class.getResource(fxmlPath);
        if (url == null) throw new IllegalArgumentException("FXML introuvable: " + fxmlPath);
        FXMLLoader loader = new FXMLLoader(url, I18n.bundle());
        return loader.load();
    }

    public static <T> Loaded<T> loadWithController(String fxmlPath) throws Exception {
        URL url = FxLoader.class.getResource(fxmlPath);
        if (url == null) throw new IllegalArgumentException("FXML introuvable: " + fxmlPath);
        FXMLLoader loader = new FXMLLoader(url, I18n.bundle());
        Parent root = loader.load();
        return new Loaded<>(root, loader.getController());
    }

    public static final class Loaded<T> {
        public final Parent root;
        public final T controller;
        public Loaded(Parent root, T controller) {
            this.root = root;
            this.controller = controller;
        }
    }
}