package com.advisora.GUI.Investissement;

import com.advisora.Model.investment.MacroAnalysis;
import com.advisora.Model.investment.MacroAnalysis.RiskLevel;
import com.advisora.Model.investment.MacroIndicators;
import com.advisora.Services.investment.MacroRiskEngine;
import com.advisora.Services.investment.WorldBankService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class MacroAnalysisController {

    // ── Status / data panes ──────────────────────────────────
    @FXML
    private Label statusLabel;
    @FXML
    private VBox dataPane;

    // ── Indicators ───────────────────────────────────────────
    @FXML
    private Label inflLabel;
    @FXML
    private Label inflYearLabel;
    @FXML
    private Label lendLabel;
    @FXML
    private Label lendYearLabel;
    @FXML
    private Label gdpLabel;
    @FXML
    private Label gdpYearLabel;

    // ── Risk ─────────────────────────────────────────────────
    @FXML
    private Label riskBadge;
    @FXML
    private Label riskLevelLabel;
    @FXML
    private Label riskScoreLabel;

    // ── ROI ──────────────────────────────────────────────────
    @FXML
    private Label inflDeductLabel;
    @FXML
    private Label inflDeductValue;
    @FXML
    private Label riskPremLabel;
    @FXML
    private Label riskPremValue;
    @FXML
    private Label adjRoiLabel;

    private Runnable onClose = () -> {
    };

    private final WorldBankService worldBank = new WorldBankService();
    private final MacroRiskEngine engine = new MacroRiskEngine();

    @FXML
    public void initialize() {
        fetchAndDisplay();
    }

    public void setOnClose(Runnable r) {
        this.onClose = r != null ? r : () -> {
        };
    }

    @FXML
    private void close() {
        onClose.run();
    }

    // ── Fetch ────────────────────────────────────────────────

    private void fetchAndDisplay() {
        showLoading();

        Task<MacroAnalysis> task = new Task<>() {
            @Override
            protected MacroAnalysis call() throws Exception {
                MacroIndicators indicators = worldBank.fetchIndicators();
                return engine.analyse(indicators);
            }
        };

        task.setOnSucceeded(ev -> populate(task.getValue()));
        task.setOnFailed(ev -> showError(task.getException()));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── UI helpers ───────────────────────────────────────────

    private void showLoading() {
        statusLabel.setText("⏳ Récupération des données World Bank...");
        statusLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 13px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        dataPane.setVisible(false);
        dataPane.setManaged(false);
    }

    private void showError(Throwable ex) {
        String msg = ex != null ? ex.getMessage() : "Erreur inconnue";
        statusLabel.setText("❌ " + msg);
        statusLabel.setStyle("-fx-text-fill: #e05252; -fx-font-size: 13px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        dataPane.setVisible(false);
        dataPane.setManaged(false);
    }

    private void populate(MacroAnalysis result) {
        MacroIndicators m = result.getData();

        // ── Indicators ────────────────────────────────────────
        inflLabel.setText(String.format("%.2f %%", m.getInflation()));
        inflYearLabel.setText("(" + m.getYear() + ")");

        lendLabel.setText(String.format("%.2f %%", m.getLendingRate()));
        lendYearLabel.setText(m.isLendingEstimated() ? "(BCT 2024 — estimé)" : "(" + m.getYear() + ")");
        if (m.isLendingEstimated()) {
            lendYearLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #E97B2C;");
        }

        gdpLabel.setText(String.format("%.2f %%", m.getGdpGrowth()));
        gdpYearLabel.setText("(" + m.getYear() + ")");

        // Color GDP label: positive=green, negative=red
        gdpLabel.setStyle(m.getGdpGrowth() >= 0
                ? "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;"
                : "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e05252;");

        // ── Risk badge ────────────────────────────────────────
        RiskLevel level = result.getRiskLevel();
        String badgeColor = switch (level) {
            case LOW -> "rgba(46,163,106,0.18)";
            case MEDIUM -> "rgba(244,197,66,0.28)";
            case HIGH -> "rgba(217,83,79,0.20)";
        };
        String textColor = switch (level) {
            case LOW -> "#2e7d32";
            case MEDIUM -> "#7a5c00";
            case HIGH -> "#c62828";
        };
        String emoji = switch (level) {
            case LOW -> "🟢";
            case MEDIUM -> "🟡";
            case HIGH -> "🔴";
        };

        riskBadge.setText(String.format("%.0f / 100", result.getScore()));
        riskBadge.setStyle(String.format(
                "-fx-font-size: 22px; -fx-font-weight: bold;" +
                        "-fx-padding: 6 16 6 16; -fx-background-radius: 999;" +
                        "-fx-background-color: %s; -fx-text-fill: %s;",
                badgeColor, textColor));

        riskLevelLabel.setText(emoji + "  " + level.name());
        riskLevelLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");

        riskScoreLabel.setText(String.format(
                "Score de risque : %.1f  |  Prime : %.1f%%", result.getScore(), result.getRiskPremium()));

        // ── ROI breakdown ─────────────────────────────────────
        inflDeductValue.setText(String.format("−%.2f %%", m.getInflation()));
        riskPremValue.setText(String.format("−%.2f %%", result.getRiskPremium()));

        double adj = result.getAdjustedROI();
        adjRoiLabel.setText(String.format("%.2f %%", adj));
        adjRoiLabel.setStyle(adj >= 0
                ? "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;"
                : "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #e05252;");

        // ── Show data pane ────────────────────────────────────
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        dataPane.setVisible(true);
        dataPane.setManaged(true);
    }
}

