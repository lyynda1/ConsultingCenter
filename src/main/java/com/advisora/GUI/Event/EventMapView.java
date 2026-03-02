package com.advisora.GUI.Event;

import com.advisora.utils.SceneThemeApplier;

import com.advisora.Services.event.EventGeocodingService;

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

        // Create WebView for map
        webView = new WebView();
        webEngine = webView.getEngine();
        
        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        
        // Container
        StackPane webContainer = new StackPane();
        webContainer.getChildren().addAll(webView, loadingIndicator);
        
        // Header
        Label lblHeader = new Label("ðŸ“ Localisation des evenements");
        lblHeader.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 10px;");
        
        // Footer
        Button btnClose = new Button("Fermer");
        btnClose.setOnAction(e -> stage.close());
        
        HBox footer = new HBox(10, btnClose);
        footer.setStyle("-fx-padding: 10px; -fx-alignment: center-right;");
        
        // Layout
        BorderPane root = new BorderPane();
        root.setTop(lblHeader);
        root.setCenter(webContainer);
        root.setBottom(footer);
        
        Scene scene = SceneThemeApplier.createScene(root);
        SceneThemeApplier.setScene(stage, scene);
        
        // Setup WebEngine listeners
        setupWebEngineListeners();
    }

    private void setupWebEngineListeners() {
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.RUNNING) {
                loadingIndicator.setVisible(true);
            } else if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED) {
                loadingIndicator.setVisible(false);
            }
        });
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
        double centerLat = 36.8065;
        double centerLon = 10.1815;
        int markerCount = 0;
        
        for (int i = 0; i < locations.length; i++) {
            String loc = locations[i];
            if (loc == null || loc.isBlank()) continue;
            
            // Get coordinates from geocoding service
            double[] coords = EventGeocodingService.getCoordinates(loc);
            double lat = coords[0];
            double lon = coords[1];
            
            // Extract event title (before the dash)
            String title = loc.contains(" - ") ? loc.substring(0, loc.indexOf(" - ")) : loc;
            String location = loc.contains(" - ") ? loc.substring(loc.indexOf(" - ") + 3) : loc;
            
            markers.append(String.format(
                "L.marker([%f, %f]).addTo(map).bindPopup('<b>%s</b><br><small>%s</small>');",
                lat, lon, escapeHtml(title), escapeHtml(location)
            ));
            
            // Update center to average of markers
            if (i == 0) {
                centerLat = lat;
                centerLon = lon;
            }
            markerCount++;
        }
        
        if (markerCount == 0) {
            // Default to Tunisia if no locations
            markers.append("L.marker([36.8065, 10.1815]).addTo(map).bindPopup('<b>Tunisie</b>');");
        }

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8"/>
                <title>Event Map</title>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body { margin: 0; padding: 0; }
                    #map { width: 100%%; height: 100vh; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map').setView([%f, %f], 8);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: 'Â© OpenStreetMap contributors',
                        maxZoom: 18
                    }).addTo(map);
                    %s
                </script>
            </body>
            </html>
            """, centerLat, centerLon, markers.toString());
    }

    private String generateSingleLocationHTML(String location, String title) {
        // Get coordinates from geocoding service
        double[] coords = EventGeocodingService.getCoordinates(location);
        double lat = coords[0];
        double lon = coords[1];
        
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8"/>
                <title>Event Location</title>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body { margin: 0; padding: 0; }
                    #map { width: 100%%; height: 100vh; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map').setView([%f, %f], 13);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: 'Â© OpenStreetMap contributors',
                        maxZoom: 18
                    }).addTo(map);
                    L.marker([%f, %f]).addTo(map)
                        .bindPopup('<b>%s</b><br>%s')
                        .openPopup();
                </script>
            </body>
            </html>
            """, lat, lon, lat, lon, escapeHtml(title), escapeHtml(location));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}



