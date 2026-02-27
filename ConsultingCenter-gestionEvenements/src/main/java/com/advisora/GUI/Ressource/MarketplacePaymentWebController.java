package com.advisora.GUI.Ressource;

import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class MarketplacePaymentWebController {
    @FXML private WebView paymentWebView;
    @FXML private Label lblWebStatus;

    private String paymentUrl;

    @FXML
    public void initialize() {
        WebEngine engine = paymentWebView.getEngine();
        engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (lblWebStatus == null) {
                return;
            }
            if (newUrl == null || newUrl.isBlank()) {
                return;
            }
            String lower = newUrl.toLowerCase();
            if (lower.contains("success")) {
                lblWebStatus.setText("Paiement termine (success). Vous pouvez confirmer.");
            } else if (lower.contains("cancel")) {
                lblWebStatus.setText("Paiement annule.");
            } else {
                lblWebStatus.setText("Chargement checkout...");
            }
        });
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (lblWebStatus == null) {
                return;
            }
            if (newState == Worker.State.SUCCEEDED) {
                if (lblWebStatus.getText() == null || lblWebStatus.getText().isBlank()) {
                    lblWebStatus.setText("Checkout charge.");
                }
            } else if (newState == Worker.State.FAILED) {
                lblWebStatus.setText("Echec chargement checkout.");
            }
        });
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl == null ? "" : paymentUrl.trim();
        if (lblWebStatus != null) {
            lblWebStatus.setText("Chargement checkout...");
        }
        if (paymentWebView != null && !this.paymentUrl.isBlank()) {
            paymentWebView.getEngine().load(this.paymentUrl);
        }
    }

    @FXML
    private void onReload() {
        if (paymentWebView != null) {
            paymentWebView.getEngine().reload();
        }
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) (paymentWebView != null && paymentWebView.getScene() != null
                ? paymentWebView.getScene().getWindow()
                : null);
        if (stage != null) {
            stage.close();
        }
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }
}
