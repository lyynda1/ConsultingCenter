package com.advisora.GUI.Project;

import com.advisora.Model.estimation.EstimateRequest;
import com.advisora.Model.estimation.EstimateResponse;
import com.advisora.Services.estimation.EstimateService;
import com.advisora.enums.EstimationStatus;
import com.advisora.enums.ScopeSize;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.Locale;

public class EstimationDialogController {
    @FXML private ComboBox<String> cmbCategory;
    @FXML private ComboBox<String> cmbScope;
    @FXML private Spinner<Integer> spComplexity;
    @FXML private ComboBox<String> cmbCurrency;
    @FXML private TextField txtBudgetCap;

    @FXML private Label lblStatus;
    @FXML private Label lblScore;
    @FXML private Label lblDecision;
    @FXML private Label lblConfidence;
    @FXML private Label lblRisk;
    @FXML private Label lblBudgetFit;
    @FXML private Label lblCostRange;
    @FXML private Label lblFx;

    @FXML private ListView<String> listRecommendations;
    @FXML private LineChart<Number, Number> sensitivityChart;
    @FXML private Label lblChartTitle;
    @FXML private Label lblChartLegend;

    private final EstimateService estimateService = new EstimateService();

    private Runnable onBack;

    @FXML
    public void initialize() {
        cmbCategory.setItems(FXCollections.observableArrayList(
                "IT / Logiciel & SaaS",
                "Finance / FinTech",
                "Marketing & Communication",
                "Sante / Healthcare",
                "E-commerce & Retail",
                "Industrie & Production",
                "Education / EdTech",
                "Logistique & Supply Chain",
                "Construction & Immobilier",
                "Secteur public / Administration"
        ));
        cmbCategory.getSelectionModel().selectFirst();

        cmbScope.setItems(FXCollections.observableArrayList("S", "M", "L"));
        cmbScope.getSelectionModel().select("M");

        cmbCurrency.setItems(FXCollections.observableArrayList("EUR", "TND", "USD"));
        cmbCurrency.getSelectionModel().select("EUR");

        spComplexity.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 3));
        spComplexity.setEditable(true);

        listRecommendations.setItems(FXCollections.observableArrayList());
        sensitivityChart.setAnimated(false);
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @FXML
    private void onEstimate() {
        try {
            EstimateRequest request = buildRequest();
            EstimateResponse response = estimateService.estimate(request);
            renderResponse(response);
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onClose() {
        if (onBack != null) {
            onBack.run();
            return;
        }
        closeStage();
    }

    private EstimateRequest buildRequest() {
        String category = extractCategoryCode(required(cmbCategory.getValue(), "category is required"));
        ScopeSize scope = ScopeSize.fromValue(cmbScope.getValue());
        int complexity = spComplexity.getValue() == null ? 0 : spComplexity.getValue();
        int durationDays = 45;
        String country = "TN";
        String currency = required(cmbCurrency.getValue(), "displayCurrency is required").toUpperCase(Locale.ROOT);
        Double budgetCap = parseRequiredNonNegative(txtBudgetCap.getText(), "budgetCap");

        return new EstimateRequest(category, scope, complexity, durationDays, country, currency, budgetCap);
    }

    private void renderResponse(EstimateResponse response) {
        lblStatus.setText(response.status().name());
        lblScore.setText(String.valueOf(response.score()));
        lblDecision.setText(decisionText(response));
        lblConfidence.setText(confidenceText(response.score()));
        lblRisk.setText(response.riskLevel().name());
        lblCostRange.setText(String.format(Locale.US, "Min %.0f | Probable %.0f | Max %.0f %s",
                response.cost().min(),
                response.cost().p50(),
                response.cost().max(),
                response.cost().currency()));
        lblBudgetFit.setText(budgetFitText(response));

        if (response.fx().used()) {
            lblFx.setText("Conversion appliquee (EUR -> " + response.cost().currency() + ") au taux " + response.fx().rate() + " du " + response.fx().date());
        } else {
            lblFx.setText("Aucune conversion (devise de base EUR).");
        }

        listRecommendations.setItems(FXCollections.observableArrayList(response.recommendations()));
        renderChart(response);
    }

    private String decisionText(EstimateResponse response) {
        return switch (response.status()) {
            case OK -> "GO - lancement recommande";
            case WARNING -> "GO sous conditions";
            case BLOCKED -> "NO-GO temporaire";
        };
    }

    private String confidenceText(int score) {
        if (score >= 75) return "Elevee (" + score + "/100)";
        if (score >= 55) return "Moyenne (" + score + "/100)";
        return "Faible (" + score + "/100)";
    }

    private String budgetFitText(EstimateResponse response) {
        double p50 = response.cost().p50();
        double max = response.cost().max();
        if (response.status() == EstimationStatus.BLOCKED) {
            return "Budget probablement insuffisant pour demarrer proprement (viser au moins " + Math.round(p50) + ").";
        }
        if (response.status() == EstimationStatus.WARNING) {
            return "Budget possible mais sensible aux aleas. Prevoir une marge jusqu'a " + Math.round(max) + ".";
        }
        return "Budget coherent avec le lancement. Reserve recommandee: 8-12%.";
    }

    private void renderChart(EstimateResponse response) {
        sensitivityChart.getData().clear();
        updateChartMetadata(response);
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(response.chart().type());
        for (var point : response.chart().points()) {
            series.getData().add(new XYChart.Data<>(point.x(), point.y()));
        }
        sensitivityChart.getData().add(series);
    }

    private void updateChartMetadata(EstimateResponse response) {
        if ("LAUNCH_READINESS".equalsIgnoreCase(response.chart().type())) {
            lblChartTitle.setText("Launch Readiness Chart");
            lblChartLegend.setText("1=Strategie  2=Budget  3=Execution  4=Gouvernance  5=Risque (score/100)");
            if (sensitivityChart.getXAxis() instanceof NumberAxis xAxis) {
                xAxis.setLabel("Critere");
                xAxis.setAutoRanging(false);
                xAxis.setLowerBound(1);
                xAxis.setUpperBound(5);
                xAxis.setTickUnit(1);
            }
            if (sensitivityChart.getYAxis() instanceof NumberAxis yAxis) {
                yAxis.setLabel("Score de preparation");
                yAxis.setAutoRanging(false);
                yAxis.setLowerBound(0);
                yAxis.setUpperBound(100);
                yAxis.setTickUnit(10);
            }
            return;
        }

        lblChartTitle.setText("Sensitivity Chart");
        lblChartLegend.setText("");
        if (sensitivityChart.getXAxis() instanceof NumberAxis xAxis) {
            xAxis.setLabel("X");
            xAxis.setAutoRanging(true);
        }
        if (sensitivityChart.getYAxis() instanceof NumberAxis yAxis) {
            yAxis.setLabel("Y");
            yAxis.setAutoRanging(true);
        }
    }

    private Double parseRequiredNonNegative(String raw, String field) {
        try {
            double value = Double.parseDouble(required(raw, field + " is required"));
            if (value < 0) throw new IllegalArgumentException(field + " must be >= 0");
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String extractCategoryCode(String label) {
        String v = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (v.contains("logiciel") || v.contains("saas") || v.startsWith("it")) return "IT";
        if (v.contains("finance") || v.contains("fintech")) return "FINANCE";
        if (v.contains("marketing")) return "MARKETING";
        if (v.contains("sante") || v.contains("health")) return "HEALTHCARE";
        if (v.contains("e-commerce") || v.contains("retail")) return "ECOMMERCE";
        if (v.contains("industrie") || v.contains("production")) return "INDUSTRY";
        if (v.contains("education") || v.contains("edtech")) return "EDUCATION";
        if (v.contains("logistique") || v.contains("supply")) return "LOGISTICS";
        if (v.contains("construction") || v.contains("immobilier")) return "CONSTRUCTION";
        if (v.contains("public") || v.contains("administration")) return "PUBLIC";
        return "OTHER";
    }

    private void closeStage() {
        Stage stage = (Stage) lblStatus.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Estimation Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

}
