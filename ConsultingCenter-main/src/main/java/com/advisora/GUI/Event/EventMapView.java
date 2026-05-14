package com.advisora.GUI.Event;

import com.advisora.Services.event.EventGeocodingService;
import com.advisora.utils.SceneThemeApplier;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class EventMapView {

    private final Stage stage;
    private final WebView webView;
    private final WebEngine webEngine;
    private final ProgressIndicator loadingIndicator;

    public EventMapView() {
        stage = new Stage();
        stage.setTitle("Carte des evenements");
        stage.setWidth(1000);
        stage.setHeight(700);

        webView = new WebView();
        webEngine = webView.getEngine();

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setVisible(false);

        StackPane webContainer = new StackPane();
        webContainer.getChildren().addAll(webView, loadingIndicator);
        StackPane.setMargin(loadingIndicator, new Insets(8));

        Label lblHeader = new Label("\uD83D\uDCCD Localisation des evenements");
        lblHeader.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px;");

        Button btnClose = new Button("Fermer");
        btnClose.setOnAction(e -> stage.close());

        HBox footer = new HBox(10, btnClose);
        footer.setStyle("-fx-padding: 10px; -fx-alignment: center-right;");

        BorderPane root = new BorderPane();
        root.setTop(lblHeader);
        root.setCenter(webContainer);
        root.setBottom(footer);

        Scene scene = SceneThemeApplier.createScene(root);
        SceneThemeApplier.setScene(stage, scene);

        setupWebEngineListeners();
        stage.setOnShown(e -> requestLeafletRelayout());
        stage.widthProperty().addListener((obs, oldV, newV) -> requestLeafletRelayout());
        stage.heightProperty().addListener((obs, oldV, newV) -> requestLeafletRelayout());
    }

    private void setupWebEngineListeners() {
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.RUNNING) {
                loadingIndicator.setVisible(true);
            } else if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                loadingIndicator.setVisible(false);
                if (newState == Worker.State.SUCCEEDED) {
                    requestLeafletRelayout();
                }
            }
        });
    }

    private void requestLeafletRelayout() {
        try {
            webEngine.executeScript(
                    "if (window.__map) {" +
                    "  setTimeout(function(){ window.__map.invalidateSize(true); }, 0);" +
                    "  setTimeout(function(){ window.__map.invalidateSize(true); }, 250);" +
                    "}"
            );
        } catch (Exception ignored) {
            // Ignore when page is not ready yet.
        }
    }

    public void showMapWithLocations(String... locations) {
        String html = generateMapHTML(locations);
        webEngine.loadContent(html);
        stage.show();
    }

    public void showMapWithSingleLocation(String location, String title) {
        String html = generateSingleLocationHTML(location, title);
        webEngine.loadContent(html);
        stage.show();
    }

    private String generateMapHTML(String... locations) {
        StringBuilder markers = new StringBuilder();
        StringBuilder points = new StringBuilder();
        double centerLat = 36.8065;
        double centerLon = 10.1815;
        int markerCount = 0;

        for (int i = 0; i < locations.length; i++) {
            String loc = locations[i];
            if (loc == null || loc.isBlank()) {
                continue;
            }

            double[] coords = EventGeocodingService.getCoordinates(loc);
            double lat = coords[0];
            double lon = coords[1];

            String title = loc.contains(" - ") ? loc.substring(0, loc.indexOf(" - ")) : loc;
            String place = loc.contains(" - ") ? loc.substring(loc.indexOf(" - ") + 3) : loc;

            markers.append(String.format(
                    "L.marker([%f, %f]).addTo(window.__map).bindPopup('<b>%s</b><br><small>%s</small>');%n",
                    lat, lon, escapeHtml(title), escapeHtml(place)
            ));
            points.append(String.format("[%f,%f],", lat, lon));

            if (i == 0) {
                centerLat = lat;
                centerLon = lon;
            }
            markerCount++;
        }

        if (markerCount == 0) {
            markers.append("L.marker([36.8065, 10.1815]).addTo(window.__map).bindPopup('<b>Tunisie</b>');");
            points.append("[36.8065,10.1815],");
        }

        String pointsJs = points.length() > 0
                ? points.substring(0, points.length() - 1)
                : "[36.8065,10.1815]";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8"/>
                <title>Event Map</title>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.8.0/dist/leaflet.css"/>
                <script src="https://unpkg.com/leaflet@1.8.0/dist/leaflet.js"></script>
                <style>
                    html, body, #map { margin: 0; padding: 0; width: 100%%; height: 100%%; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    // JavaFX WebView workaround: Leaflet 1.9.x can render with desynced tiles/markers.
                    // 1.8.0 + no 3D animations is more stable.
                    L.Browser.any3d = false;
                    window.__map = L.map('map', {
                        zoomAnimation: false,
                        fadeAnimation: false,
                        markerZoomAnimation: false,
                        inertia: false,
                        preferCanvas: true
                    }).setView([%f, %f], 8);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '&copy; OpenStreetMap contributors',
                        maxZoom: 18
                    }).addTo(window.__map);
                    var points = [%s];
                    %s
                    if (points.length > 1) {
                        window.__map.fitBounds(points, { padding: [30, 30] });
                    }
                    setTimeout(function(){ window.__map.invalidateSize(true); }, 150);
                    setTimeout(function(){ window.__map.invalidateSize(true); }, 600);
                </script>
            </body>
            </html>
            """, centerLat, centerLon, pointsJs, markers.toString());
    }

    private String generateSingleLocationHTML(String location, String title) {
        double[] coords = EventGeocodingService.getCoordinates(location);
        double lat = coords[0];
        double lon = coords[1];

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8"/>
                <title>Event Location</title>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.8.0/dist/leaflet.css"/>
                <script src="https://unpkg.com/leaflet@1.8.0/dist/leaflet.js"></script>
                <style>
                    html, body, #map { margin: 0; padding: 0; width: 100%%; height: 100%%; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    // JavaFX WebView workaround: force non-animated 2D path.
                    L.Browser.any3d = false;
                    window.__map = L.map('map', {
                        zoomAnimation: false,
                        fadeAnimation: false,
                        markerZoomAnimation: false,
                        inertia: false,
                        preferCanvas: true
                    }).setView([%f, %f], 13);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '&copy; OpenStreetMap contributors',
                        maxZoom: 18
                    }).addTo(window.__map);
                    L.marker([%f, %f]).addTo(window.__map)
                        .bindPopup('<b>%s</b><br>%s')
                        .openPopup();
                    setTimeout(function(){ window.__map.invalidateSize(true); }, 150);
                    setTimeout(function(){ window.__map.invalidateSize(true); }, 600);
                </script>
            </body>
            </html>
            """, lat, lon, lat, lon, escapeHtml(title), escapeHtml(location));
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
