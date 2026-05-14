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

    // â”€â”€ Status / data panes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @FXML
    private Label statusLabel;
    @FXML
    private VBox dataPane;

    // â”€â”€ Indicators â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€ Risk â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @FXML
    private Label riskBadge;
    @FXML
    private Label riskLevelLabel;
    @FXML
    private Label riskScoreLabel;

    // â”€â”€ ROI â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    // â”€â”€ Fetch â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ UI helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void showLoading() {
        statusLabel.setText("â³ RÃ©cupÃ©ration des donnÃ©es World Bank...");
        statusLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 13px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        dataPane.setVisible(false);
        dataPane.setManaged(false);
    }

    private void showError(Throwable ex) {
        String msg = ex != null ? ex.getMessage() : "Erreur inconnue";
        statusLabel.setText("âŒ " + msg);
        statusLabel.setStyle("-fx-text-fill: #e05252; -fx-font-size: 13px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
        dataPane.setVisible(false);
        dataPane.setManaged(false);
    }

    private void populate(MacroAnalysis result) {
        MacroIndicators m = result.getData();

        // â”€â”€ Indicators â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        inflLabel.setText(String.format("%.2f %%", m.getInflation()));
        inflYearLabel.setText("(" + m.getYear() + ")");

        lendLabel.setText(String.format("%.2f %%", m.getLendingRate()));
        lendYearLabel.setText(m.isLendingEstimated() ? "(BCT 2024 â€” estimÃ©)" : "(" + m.getYear() + ")");
        if (m.isLendingEstimated()) {
            lendYearLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #E97B2C;");
        }

        gdpLabel.setText(String.format("%.2f %%", m.getGdpGrowth()));
        gdpYearLabel.setText("(" + m.getYear() + ")");

        // Color GDP label: positive=green, negative=red
        gdpLabel.setStyle(m.getGdpGrowth() >= 0
                ? "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;"
                : "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e05252;");

        // â”€â”€ Risk badge â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            case LOW -> "ðŸŸ¢";
            case MEDIUM -> "ðŸŸ¡";
            case HIGH -> "ðŸ”´";
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

        // â”€â”€ ROI breakdown â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        inflDeductValue.setText(String.format("âˆ’%.2f %%", m.getInflation()));
        riskPremValue.setText(String.format("âˆ’%.2f %%", result.getRiskPremium()));

        double adj = result.getAdjustedROI();
        adjRoiLabel.setText(String.format("%.2f %%", adj));
        adjRoiLabel.setStyle(adj >= 0
                ? "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;"
                : "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #e05252;");

        // â”€â”€ Show data pane â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        dataPane.setVisible(true);
        dataPane.setManaged(true);
    }
}


