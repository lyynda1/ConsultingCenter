package com.advisora.GUI.Event;

import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class StripePaymentDialog {
    
    private final Stage dialog;
    private final WebView webView;
    private final WebEngine webEngine;
    private final ProgressIndicator loadingIndicator;
    private final StackPane webContainer;
    
    private boolean paymentCompleted = false;
    private boolean userCancelled = false;

    public StripePaymentDialog(Stage owner) {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Paiement Stripe");
        dialog.setWidth(800);
        dialog.setHeight(700);

        // Create WebView for embedded payment
        webView = new WebView();
        webEngine = webView.getEngine();
        
        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        
        // Container for WebView and loading indicator
        webContainer = new StackPane();
        webContainer.getChildren().addAll(webView, loadingIndicator);
        
        // Header
        Label lblHeader = new Label("Paiement securise via Stripe");
        lblHeader.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10px;");
        
        // Footer with buttons
        Button btnCancel = new Button("Annuler");
        btnCancel.setOnAction(e -> {
            userCancelled = true;
            dialog.close();
        });
        
        Button btnConfirm = new Button("J'ai termine le paiement");
        btnConfirm.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        btnConfirm.setOnAction(e -> {
            paymentCompleted = true;
            dialog.close();
        });
        
        HBox footer = new HBox(10, btnCancel, btnConfirm);
        footer.setStyle("-fx-padding: 10px; -fx-alignment: center;");
        
        // Layout
        BorderPane root = new BorderPane();
        root.setTop(lblHeader);
        root.setCenter(webContainer);
        root.setBottom(footer);
        
        Scene scene = new Scene(root);
        dialog.setScene(scene);
        
        // Setup WebEngine listeners
        setupWebEngineListeners();
    }

    private void setupWebEngineListeners() {
        // Show/hide loading indicator
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.RUNNING) {
                loadingIndicator.setVisible(true);
            } else if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED) {
                loadingIndicator.setVisible(false);
            }
        });
        
        // Monitor URL changes to detect success/cancel
        webEngine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (newUrl != null) {
                // Check if redirected to success URL
                if (newUrl.contains("/success") || newUrl.contains("payment_intent=") || 
                    newUrl.contains("payment_status=paid")) {
                    paymentCompleted = true;
                    dialog.close();
                }
                // Check if redirected to cancel URL
                else if (newUrl.contains("/cancel")) {
                    userCancelled = true;
                    dialog.close();
                }
            }
        });
        
        // Handle load errors
        webEngine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null) {
                loadingIndicator.setVisible(false);
                showError("Erreur de chargement", 
                    "Impossible de charger la page de paiement.\n" + newEx.getMessage());
            }
        });
    }

    public void loadPaymentUrl(String paymentUrl) {
        if (paymentUrl == null || paymentUrl.isBlank()) {
            showError("URL invalide", "L'URL de paiement est vide");
            return;
        }
        
        loadingIndicator.setVisible(true);
        webEngine.load(paymentUrl);
    }

    public boolean showAndWait() {
        dialog.showAndWait();
        return paymentCompleted && !userCancelled;
    }

    public boolean isPaymentCompleted() {
        return paymentCompleted;
    }

    public boolean isUserCancelled() {
        return userCancelled;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
