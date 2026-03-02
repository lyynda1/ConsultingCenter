package com.advisora.GUI.Investissement;

import com.advisora.Services.investment.ExchangeRateService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.util.Map;

public class ExchangeRateController {

    @FXML
    private Label statusLabel;
    @FXML
    private GridPane ratesGrid;
    @FXML
    private Label usdLabel;
    @FXML
    private Label eurLabel;
    @FXML
    private Label gbpLabel;
    @FXML
    private Label noteLabel;

    private Runnable onClose = () -> {
    };

    private final ExchangeRateService service = new ExchangeRateService();

    @FXML
    public void initialize() {
        fetchRates();
    }

    public void setOnClose(Runnable r) {
        this.onClose = r != null ? r : () -> {
        };
    }

    @FXML
    private void close() {
        onClose.run();
    }

    private void fetchRates() {
        showLoading();

        Task<Map<String, Double>> task = new Task<>() {
            @Override
            protected Map<String, Double> call() throws Exception {
                return service.getRates();
            }
        };

        task.setOnSucceeded(ev -> showRates(task.getValue()));
        task.setOnFailed(ev -> showError(task.getException()));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void showLoading() {
        statusLabel.setText("⏳ Chargement des taux en cours...");
        statusLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 13px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        ratesGrid.setVisible(false);
        ratesGrid.setManaged(false);
        noteLabel.setVisible(false);
        noteLabel.setManaged(false);
    }

    private void showRates(Map<String, Double> rates) {
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        usdLabel.setText(rates.containsKey("USD") ? String.format("%.4f", rates.get("USD")) : "—");
        eurLabel.setText(rates.containsKey("EUR") ? String.format("%.4f", rates.get("EUR")) : "—");
        gbpLabel.setText(rates.containsKey("GBP") ? String.format("%.4f", rates.get("GBP")) : "—");

        ratesGrid.setVisible(true);
        ratesGrid.setManaged(true);

        noteLabel.setText("Taux mis à jour en temps réel via ExchangeRate-API");
        noteLabel.setVisible(true);
        noteLabel.setManaged(true);
    }

    private void showError(Throwable ex) {
        statusLabel.setText("❌ " + (ex != null ? ex.getMessage() : "Erreur inconnue"));
        statusLabel.setStyle("-fx-text-fill: #e05252; -fx-font-size: 13px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        ratesGrid.setVisible(false);
        ratesGrid.setManaged(false);
    }
}

