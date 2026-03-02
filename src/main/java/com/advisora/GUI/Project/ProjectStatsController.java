package com.advisora.GUI.Project;

import com.advisora.Model.projet.ProjectClientStat;
import com.advisora.Model.projet.ProjectDashboardData;
import com.advisora.Model.projet.ProjectStatsSummary;
import com.advisora.Model.projet.ProjectTypeStat;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Locale;

public class ProjectStatsController {
    @FXML private Label lblHeader;
    @FXML private Button btnBack;
    @FXML private PieChart chartStatus;
    @FXML private BarChart<String, Number> chartTypeRate;
    @FXML private BarChart<String, Number> chartClientRate;
    @FXML private BarChart<String, Number> chartGlobalKpi;
    @FXML private VBox clientChartBox;
    private Runnable onBack;

    public void init(ProjectDashboardData data, UserRole role) {
        ProjectStatsSummary summary = data == null ? null : data.getSummary();
        boolean managerView = role == UserRole.GERANT || role == UserRole.ADMIN;

        if (lblHeader != null) {
            lblHeader.setText(managerView ? "Statistiques graphiques - Gerant/Admin" : "Statistiques graphiques - Client");
        }

        if (clientChartBox != null) {
            clientChartBox.setVisible(managerView);
            clientChartBox.setManaged(managerView);
        }

        loadStatusPie(summary);
        loadTypeRateChart(data == null ? List.of() : data.getTypeStats());
        loadClientRateChart(data == null ? List.of() : data.getClientStats());
        loadGlobalKpiChart(summary);
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
        if (btnBack != null) {
            btnBack.setText(onBack == null ? "Fermer" : "Retour projets");
        }
    }

    @FXML
    private void onClose() {
        if (onBack != null) {
            onBack.run();
            return;
        }
        Stage stage = (Stage) lblHeader.getScene().getWindow();
        stage.close();
    }

    private void loadStatusPie(ProjectStatsSummary summary) {
        if (chartStatus == null) return;
        int pending = summary == null ? 0 : summary.getPending();
        int accepted = summary == null ? 0 : summary.getAccepted();
        int refused = summary == null ? 0 : summary.getRefused();

        chartStatus.setData(FXCollections.observableArrayList(
                new PieChart.Data("En attente (" + pending + ")", pending),
                new PieChart.Data("Acceptes (" + accepted + ")", accepted),
                new PieChart.Data("Refuses (" + refused + ")", refused)
        ));
    }

    private void loadTypeRateChart(List<ProjectTypeStat> rows) {
        if (chartTypeRate == null) return;
        chartTypeRate.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Taux d'acceptation %");
        int limit = Math.min(rows == null ? 0 : rows.size(), 8);
        for (int i = 0; i < limit; i++) {
            ProjectTypeStat row = rows.get(i);
            String label = row.getType() == null || row.getType().isBlank() ? "-" : row.getType();
            series.getData().add(new XYChart.Data<>(label, round(row.getAcceptanceRatePercent())));
        }
        chartTypeRate.getData().add(series);
    }

    private void loadClientRateChart(List<ProjectClientStat> rows) {
        if (chartClientRate == null) return;
        chartClientRate.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Taux client %");
        int limit = Math.min(rows == null ? 0 : rows.size(), 8);
        for (int i = 0; i < limit; i++) {
            ProjectClientStat row = rows.get(i);
            String label = row.getClientName() == null || row.getClientName().isBlank() ? ("Client #" + row.getClientId()) : row.getClientName();
            series.getData().add(new XYChart.Data<>(label, round(row.getAcceptanceRatePercent())));
        }
        chartClientRate.getData().add(series);
    }

    private void loadGlobalKpiChart(ProjectStatsSummary summary) {
        if (chartGlobalKpi == null) return;
        chartGlobalKpi.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Valeur %");
        series.getData().add(new XYChart.Data<>("Acceptation", summary == null ? 0 : round(summary.getAcceptanceRatePercent())));
        series.getData().add(new XYChart.Data<>("Avancement", summary == null ? 0 : round(summary.getAvgProgressPercent())));
        chartGlobalKpi.getData().add(series);
    }

    private double round(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.2f", Math.max(0.0, Math.min(100.0, value))));
    }
}

