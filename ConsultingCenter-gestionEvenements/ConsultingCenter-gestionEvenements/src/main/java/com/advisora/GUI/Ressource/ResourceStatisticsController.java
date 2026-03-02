package com.advisora.GUI.Ressource;

import com.advisora.Services.ressource.ResourceStatisticsPdfExportService;
import com.advisora.Services.ressource.ResourceStatisticsService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ResourceStatisticsController {
    @FXML private Label lblRoleTag;
    @FXML private Label lblScopeInfo;
    @FXML private Label lblSmartInsight;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private ComboBox<ResourceStatisticsService.LookupItem> comboUser;
    @FXML private ComboBox<ResourceStatisticsService.LookupItem> comboResource;
    @FXML private ComboBox<ResourceStatisticsService.LookupItem> comboProject;
    @FXML private ComboBox<String> comboStatus;
    @FXML private TextField txtSearch;

    @FXML private Label lblSalesValue;
    @FXML private Label lblSalesCoins;
    @FXML private Label lblReservationsValue;
    @FXML private Label lblPaymentsValue;
    @FXML private Label lblReviewsValue;
    @FXML private Label lblTrendValue;
    @FXML private Label lblResultInfo;

    @FXML private PieChart pieSalesStatus;
    @FXML private BarChart<String, Number> barTopResources;
    @FXML private BarChart<String, Number> barPaymentProviders;
    @FXML private LineChart<String, Number> lineMonthlyTrend;

    private final ResourceStatisticsService statsService = new ResourceStatisticsService();
    private final ResourceStatisticsPdfExportService pdfExportService = new ResourceStatisticsPdfExportService();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(180));
    private final UserRole currentRole = SessionContext.getCurrentRole();
    private final int currentUserId = SessionContext.getCurrentUserId();

    private boolean suppressRefresh;
    private ResourceStatisticsService.ResourceStatsFilter lastFilter;
    private ResourceStatisticsService.DashboardData lastData;

    @FXML
    public void initialize() {
        setupRoleBadge();
        loadFilterOptions();
        setupListeners();
        refreshDashboard();
    }

    @FXML
    private void onRefresh() {
        refreshDashboard();
    }

    @FXML
    private void onResetFilters() {
        suppressRefresh = true;
        try {
            dpFrom.setValue(null);
            dpTo.setValue(null);
            if (comboUser.getItems() != null && !comboUser.getItems().isEmpty()) comboUser.getSelectionModel().selectFirst();
            if (comboResource.getItems() != null && !comboResource.getItems().isEmpty()) comboResource.getSelectionModel().selectFirst();
            if (comboProject.getItems() != null && !comboProject.getItems().isEmpty()) comboProject.getSelectionModel().selectFirst();
            if (comboStatus.getItems() != null && !comboStatus.getItems().isEmpty()) comboStatus.getSelectionModel().selectFirst();
            if (txtSearch != null) txtSearch.clear();
        } finally {
            suppressRefresh = false;
        }
        refreshDashboard();
    }

    @FXML
    private void onExportPdf() {
        try {
            if (lastData == null) {
                refreshDashboard();
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter statistiques ressource (PDF)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("resource_stats_" + currentRole.name().toLowerCase(Locale.ROOT) + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".pdf");

            File selected = chooser.showSaveDialog(lblResultInfo.getScene().getWindow());
            if (selected == null) {
                return;
            }
            File out = selected.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? selected
                    : (selected.getParentFile() == null
                    ? new File(selected.getName() + ".pdf")
                    : new File(selected.getParentFile(), selected.getName() + ".pdf"));

            pdfExportService.exportDashboardReport(lastData, lastFilter, currentRole, out);
            ensureValidPdf(out);
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "PDF genere:\n" + out.getAbsolutePath(), ButtonType.OK);
            ok.setHeaderText("Export termine");
            ok.showAndWait();
        } catch (Exception ex) {
            showError("Export PDF", ex.getMessage());
        }
    }

    @FXML
    private void onClose() {
        Stage stage = lblResultInfo == null || lblResultInfo.getScene() == null ? null : (Stage) lblResultInfo.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private void setupRoleBadge() {
        if (lblRoleTag == null) return;
        lblRoleTag.setText(currentRole.name());
        lblRoleTag.getStyleClass().removeAll("status-accepted", "status-pending", "status-refused");
        if (currentRole == UserRole.ADMIN) {
            lblRoleTag.getStyleClass().add("status-accepted");
        } else if (currentRole == UserRole.GERANT) {
            lblRoleTag.getStyleClass().add("status-pending");
        } else {
            lblRoleTag.getStyleClass().add("status-refused");
        }
    }

    private void loadFilterOptions() {
        ResourceStatisticsService.FilterOptions options = statsService.loadFilterOptions(currentRole, currentUserId);
        suppressRefresh = true;
        try {
            comboUser.setItems(FXCollections.observableArrayList(options.users));
            comboResource.setItems(FXCollections.observableArrayList(options.resources));
            comboProject.setItems(FXCollections.observableArrayList(options.projects));
            comboStatus.setItems(FXCollections.observableArrayList(options.statuses));

            if (!comboUser.getItems().isEmpty()) comboUser.getSelectionModel().selectFirst();
            if (!comboResource.getItems().isEmpty()) comboResource.getSelectionModel().selectFirst();
            if (!comboProject.getItems().isEmpty()) comboProject.getSelectionModel().selectFirst();
            if (!comboStatus.getItems().isEmpty()) comboStatus.getSelectionModel().selectFirst();
        } finally {
            suppressRefresh = false;
        }
    }

    private void setupListeners() {
        dpFrom.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        dpTo.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        comboUser.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        comboResource.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        comboProject.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        comboStatus.valueProperty().addListener((obs, oldV, newV) -> triggerRefresh());
        txtSearch.textProperty().addListener((obs, oldV, newV) -> {
            if (suppressRefresh) return;
            searchDebounce.setOnFinished(e -> refreshDashboard());
            searchDebounce.playFromStart();
        });
    }

    private void triggerRefresh() {
        if (!suppressRefresh) {
            refreshDashboard();
        }
    }

    private void refreshDashboard() {
        try {
            ResourceStatisticsService.ResourceStatsFilter filter = buildFilter();
            ResourceStatisticsService.DashboardData data = statsService.loadDashboard(filter, currentRole, currentUserId);
            lastFilter = filter;
            lastData = data;
            applyKpis(data);
            applyCharts(data);
            lblScopeInfo.setText(data.scopeLabel);
            lblSmartInsight.setText(buildSmartInsight(data));
            lblResultInfo.setText("Mise a jour: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        } catch (Exception ex) {
            showError("Statistiques", ex.getMessage());
        }
    }

    private ResourceStatisticsService.ResourceStatsFilter buildFilter() {
        ResourceStatisticsService.ResourceStatsFilter f = new ResourceStatisticsService.ResourceStatsFilter();
        f.fromDate = dpFrom == null ? null : dpFrom.getValue();
        f.toDate = dpTo == null ? null : dpTo.getValue();
        f.userId = selectedId(comboUser);
        f.resourceId = selectedId(comboResource);
        f.projectId = selectedId(comboProject);
        f.status = comboStatus == null ? null : comboStatus.getValue();
        f.searchText = txtSearch == null ? "" : txtSearch.getText();
        return f;
    }

    private Integer selectedId(ComboBox<ResourceStatisticsService.LookupItem> combo) {
        if (combo == null) return null;
        ResourceStatisticsService.LookupItem item = combo.getValue();
        return item == null ? null : item.getId();
    }

    private void applyKpis(ResourceStatisticsService.DashboardData data) {
        lblSalesValue.setText(String.valueOf(data.salesCount));
        lblSalesCoins.setText(formatCoins(data.salesCoins));
        lblReservationsValue.setText(data.reservationLines + " / " + data.reservedQty);
        lblPaymentsValue.setText(data.paymentCount + " / " + formatCoins(data.paymentCoins));
        lblReviewsValue.setText(String.format(Locale.US, "%.2f (%d avis)", data.reviewAverage, data.reviewCount));
        lblTrendValue.setText(String.format(Locale.US, "%.1f%% | %s vs %s",
                data.monthlyRevenueDeltaPercent,
                formatCoins(data.currentMonthRevenue),
                formatCoins(data.previousMonthRevenue)));
        lblTrendValue.getStyleClass().removeAll("trend-up", "trend-down", "trend-flat");
        if (data.monthlyRevenueDeltaPercent > 0.01) lblTrendValue.getStyleClass().add("trend-up");
        else if (data.monthlyRevenueDeltaPercent < -0.01) lblTrendValue.getStyleClass().add("trend-down");
        else lblTrendValue.getStyleClass().add("trend-flat");
    }

    private void applyCharts(ResourceStatisticsService.DashboardData data) {
        applyStatusPie(data.statusBreakdown);
        applyTopResources(data.topResources);
        applyProviders(data.paymentProviders);
        applyMonthlyTrend(data.monthlyRevenue, data.monthlyReservations);
    }

    private void applyStatusPie(List<ResourceStatisticsService.StatusPoint> rows) {
        var pie = FXCollections.<PieChart.Data>observableArrayList();
        if (rows == null || rows.isEmpty()) {
            pie.add(new PieChart.Data("NO_DATA", 1));
        } else {
            for (ResourceStatisticsService.StatusPoint row : rows) {
                pie.add(new PieChart.Data(row.status, row.count));
            }
        }
        pieSalesStatus.setData(pie);
    }

    private void applyTopResources(List<ResourceStatisticsService.ResourcePoint> rows) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Qte vendue");
        if (rows != null) {
            for (ResourceStatisticsService.ResourcePoint row : rows) {
                series.getData().add(new XYChart.Data<>(shortLabel(row.name, 18), row.quantity));
            }
        }
        barTopResources.getData().setAll(series);
    }

    private void applyProviders(List<ResourceStatisticsService.ProviderPoint> rows) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Paiements");
        if (rows != null) {
            for (ResourceStatisticsService.ProviderPoint row : rows) {
                series.getData().add(new XYChart.Data<>(shortLabel(row.provider, 14), row.count));
            }
        }
        barPaymentProviders.getData().setAll(series);
    }

    private void applyMonthlyTrend(List<ResourceStatisticsService.MonthPoint> revenue, List<ResourceStatisticsService.MonthPoint> reservations) {
        XYChart.Series<String, Number> sRevenue = new XYChart.Series<>();
        sRevenue.setName("Ventes (coins)");
        if (revenue != null) {
            for (ResourceStatisticsService.MonthPoint row : revenue) {
                sRevenue.getData().add(new XYChart.Data<>(row.label, row.value));
            }
        }

        XYChart.Series<String, Number> sReservations = new XYChart.Series<>();
        sReservations.setName("Reservations");
        if (reservations != null) {
            for (ResourceStatisticsService.MonthPoint row : reservations) {
                sReservations.getData().add(new XYChart.Data<>(row.label, row.value));
            }
        }
        lineMonthlyTrend.getData().setAll(sRevenue, sReservations);
    }

    private String shortLabel(String value, int max) {
        if (value == null) return "-";
        String v = value.trim();
        if (v.length() <= max) return v;
        return v.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String formatCoins(double coins) {
        return String.format(Locale.US, "%.3f", coins);
    }

    private void ensureValidPdf(File file) {
        if (file == null || !file.exists() || file.length() < 10) {
            throw new IllegalStateException("PDF invalide (fichier vide ou absent).");
        }
        byte[] head = new byte[5];
        try (FileInputStream in = new FileInputStream(file)) {
            int read = in.read(head);
            String sig = read <= 0 ? "" : new String(head, 0, read, StandardCharsets.US_ASCII);
            if (!sig.startsWith("%PDF-")) {
                throw new IllegalStateException("PDF invalide (signature manquante).");
            }
        } catch (Exception e) {
            throw new IllegalStateException("PDF invalide: " + e.getMessage(), e);
        }
    }

    private String buildSmartInsight(ResourceStatisticsService.DashboardData data) {
        String trend;
        if (data.monthlyRevenueDeltaPercent > 0.01) trend = "hausse";
        else if (data.monthlyRevenueDeltaPercent < -0.01) trend = "baisse";
        else trend = "stable";

        String topName = "-";
        int topQty = 0;
        if (data.topResources != null && !data.topResources.isEmpty()) {
            ResourceStatisticsService.ResourcePoint top = data.topResources.getFirst();
            topName = top.name;
            topQty = top.quantity;
        }

        return "Insight: tendance " + trend
                + " (" + String.format(Locale.US, "%.1f%%", data.monthlyRevenueDeltaPercent) + "), "
                + "top ressource = " + topName + " (" + topQty + "), "
                + "moyenne avis = " + String.format(Locale.US, "%.2f", data.reviewAverage) + ".";
    }

    private void showError(String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }
}
