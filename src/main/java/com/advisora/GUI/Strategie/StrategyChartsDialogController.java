package com.advisora.GUI.Strategie;

import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.strategie.serviceSWOT;
import com.advisora.Model.strategie.SWOTItem;
import com.advisora.enums.SWOTType;
import com.advisora.Services.strategie.ServiceStrategie;
import com.advisora.enums.StrategyStatut;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StrategyChartsDialogController {

    @FXML private Label dragHandle;
    @FXML private StackPane chartHolder;

    @FXML private ComboBox<StrategyStatut> cmbStatus;
    @FXML private CheckBox chkHasSwot;
    @FXML private Spinner<Double> spMinRoi;
    @FXML private Spinner<Integer> spMaxRisk;

    @FXML private VBox qTopLeft, qTopRight, qBottomLeft, qBottomRight;

    private Runnable onClose = () -> {};
    private Consumer<Strategie> onStrategyClicked = s -> {};

    private final serviceSWOT swotService = new serviceSWOT();
    private final ServiceStrategie strategieService = new ServiceStrategie();

    private List<Strategie> allStrategies = List.of();

    // Keep chart references to rebuild quickly
    private NumberAxis xAxis;
    private NumberAxis yAxis;
    private ScatterChart<Number, Number> chart;

    public void setOnClose(Runnable r) { this.onClose = (r == null) ? () -> {} : r; }
    public Node getDragHandle() { return dragHandle; }

    /** When user clicks a point -> call this */
    public void setOnStrategyClicked(Consumer<Strategie> cb) {
        this.onStrategyClicked = (cb == null) ? s -> {} : cb;
    }

    public void setStrategies(List<Strategie> strategies) {
        this.allStrategies = (strategies == null) ? List.of() : new ArrayList<>(strategies);
        buildUiDefaults();
        rebuildChart();
    }

    @FXML
    public void initialize() {
        cmbStatus.getItems().setAll(StrategyStatut.values());
        chkHasSwot.setSelected(false);

        spMinRoi.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(-1000, 10000, 0, 5));
        spMaxRisk.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10000, 9999, 1));

        // nicer: allow typing
        spMinRoi.setEditable(true);
        spMaxRisk.setEditable(true);
    }

    private void buildUiDefaults() {
        // Sensible default maxRisk: based on current data
        int maxRisk = allStrategies.stream().mapToInt(s -> computeRiskScore(s.getId())).max().orElse(50);
        spMaxRisk.getValueFactory().setValue(Math.max(10, maxRisk));
    }

    @FXML
    private void onApplyFilters() {
        rebuildChart();
    }

    @FXML
    private void onResetFilters() {
        cmbStatus.setValue(null);
        chkHasSwot.setSelected(false);
        spMinRoi.getValueFactory().setValue(0.0);
        int maxRisk = allStrategies.stream().mapToInt(s -> computeRiskScore(s.getId())).max().orElse(50);
        spMaxRisk.getValueFactory().setValue(Math.max(10, maxRisk));
        rebuildChart();
    }
    private void hideSeriesSymbols(XYChart.Series<Number, Number> s) {
        for (XYChart.Data<Number, Number> d : s.getData()) {
            Node n = d.getNode();
            if (n != null) {
                n.setVisible(false);
                n.setManaged(false);
            }
        }
    }

    private void rebuildChart() {
        // filtered list
        List<Strategie> strategies = applyFilters(allStrategies);

        // compute points first
        List<Point> points = strategies.stream()
                .map(s -> new Point(s, computeRiskScore(s.getId()), computeRoiPercent(s), s.getBudgetTotal()))
                .collect(Collectors.toList());

        // axis bounds
        int maxRisk = points.stream().mapToInt(p -> p.risk).max().orElse(10);
        double minRoi = points.stream().mapToDouble(p -> p.roi).min().orElse(0);
        double maxRoi = points.stream().mapToDouble(p -> p.roi).max().orElse(10);

        // padding for axes
        double roiPad = Math.max(5, (maxRoi - minRoi) * 0.10);
        int axisMax = Math.max(40, maxRisk + 5);
        xAxis = new NumberAxis(0, axisMax, axisMax / 10.0);
        // Build axes + chart
        xAxis.setLabel("Risk score (Weakness + Threat weights)");

        yAxis = new NumberAxis(
                Math.floor((minRoi - roiPad) / 10) * 10,
                Math.ceil((maxRoi + roiPad) / 10) * 10,
                Math.max(5, (maxRoi - minRoi + 2 * roiPad) / 10.0)
        );
        yAxis.setLabel("ROI (%)");

        chart = new ScatterChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false); // we’ll animate nodes ourselves
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);

        // Main series
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (Point p : points) {

            // ✅ Add jitter ONLY for risk=0 cluster
            double x = p.risk;

            if (p.risk == 0) {
                // stable jitter based on id (no random movement on refresh)
                double u = ((p.s.getId() * 37) % 100) / 100.0;   // 0..0.99
                double jitter = (u * 1.6) - 0.8;                 // [-0.8 .. +0.8]
                x = Math.max(0, 0.8 + jitter);                   // center around ~0.8 (not exactly 0)
            }

            XYChart.Data<Number, Number> d = new XYChart.Data<>(x, p.roi);
            series.getData().add(d);

            d.nodeProperty().addListener((obs, oldN, newN) -> {
                if (newN != null) {
                    stylePointNode(newN, p);
                    installTooltip(newN, p);
                    installInteractions(newN, p);
                    playAppearAnim(newN);
                }
            });
        }

        // Quadrant lines (average thresholds)
        double avgRisk = points.stream()
                .mapToInt(p -> p.risk)
                .filter(r -> r > 0)
                .average()
                .orElse(10); // fallback threshold

        double avgRoi = points.stream()
                .mapToDouble(p -> p.roi)
                .average()
                .orElse(0);

        XYChart.Series<Number, Number> vLine = new XYChart.Series<>();
        vLine.getData().add(new XYChart.Data<>(avgRisk, yAxis.getLowerBound()));
        vLine.getData().add(new XYChart.Data<>(avgRisk, yAxis.getUpperBound()));

        XYChart.Series<Number, Number> hLine = new XYChart.Series<>();
        hLine.getData().add(new XYChart.Data<>(xAxis.getLowerBound(), avgRoi));
        hLine.getData().add(new XYChart.Data<>(xAxis.getUpperBound(), avgRoi));

        chart.getData().setAll(series, vLine, hLine);
        chart.applyCss();
        chart.layout();

// style series nodes (so CSS hits)
        if (vLine.getNode() != null) vLine.getNode().getStyleClass().add("chart-line-vertical");
        if (hLine.getNode() != null) hLine.getNode().getStyleClass().add("chart-line-horizontal");

// ✅ hide endpoint symbols so no clutter/labels
        hideSeriesSymbols(vLine);
        hideSeriesSymbols(hLine);

        // Add chart to holder (keep quadrant labels on top)
        chartHolder.getChildren().removeIf(n -> n instanceof ScatterChart);
        chartHolder.getChildren().add(0, chart);
        chart.applyCss();
        chart.layout();

// style the series nodes
        if (vLine.getNode() != null) vLine.getNode().getStyleClass().add("chart-line-vertical");
        if (hLine.getNode() != null) hLine.getNode().getStyleClass().add("chart-line-horizontal");

// hide the two endpoint symbols (so only the dashed line remains)
        for (XYChart.Data<Number, Number> d : vLine.getData()) {
            if (d.getNode() != null) d.getNode().setVisible(false);
        }
        for (XYChart.Data<Number, Number> d : hLine.getData()) {
            if (d.getNode() != null) d.getNode().setVisible(false);
        }

        // style line series nodes after they appear
        applyLineSeriesStyle(vLine, "chart-line-vertical");
        applyLineSeriesStyle(hLine, "chart-line-horizontal");

        // Position quadrant labels (StackPane alignment via CSS)
        qTopLeft.setMaxWidth(220);
        qTopRight.setMaxWidth(220);
        qBottomLeft.setMaxWidth(220);
        qBottomRight.setMaxWidth(220);

        qTopLeft.setPickOnBounds(false);
        qTopRight.setPickOnBounds(false);
        qBottomLeft.setPickOnBounds(false);
        qBottomRight.setPickOnBounds(false);

        qTopLeft.setMouseTransparent(true);
        qTopRight.setMouseTransparent(true);
        qBottomLeft.setMouseTransparent(true);
        qBottomRight.setMouseTransparent(true);

        StackPane.setAlignment(qTopLeft, Pos.TOP_LEFT);
        StackPane.setAlignment(qTopRight, Pos.TOP_RIGHT);
        StackPane.setAlignment(qBottomLeft, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(qBottomRight, Pos.BOTTOM_RIGHT);

        StackPane.setMargin(qTopLeft, new Insets(12));
        StackPane.setMargin(qTopRight, new Insets(12));
        StackPane.setMargin(qBottomLeft, new Insets(12));
        StackPane.setMargin(qBottomRight, new Insets(12));

    }

    private void applyLineSeriesStyle(XYChart.Series<Number, Number> line, String styleClass) {
        // series node created later, schedule listener
        line.nodeProperty().addListener((obs, oldN, newN) -> {
            if (newN != null) newN.getStyleClass().add(styleClass);
        });
    }

    private List<Strategie> applyFilters(List<Strategie> src) {
        StrategyStatut status = cmbStatus.getValue();
        boolean hasSwotOnly = chkHasSwot.isSelected();
        double minRoi = spMinRoi.getValue();
        int maxRisk = spMaxRisk.getValue();

        return src.stream()
                .filter(s -> status == null || s.getStatut() == status)
                .filter(s -> !hasSwotOnly || swotService.countByStrategie(s.getId()) > 0)
                .filter(s -> computeRoiPercent(s) >= minRoi)
                .filter(s -> computeRiskScore(s.getId()) <= maxRisk)
                .collect(Collectors.toList());
    }

    private void stylePointNode(Node node, Point p) {
        node.getStyleClass().add("chart-point");

        // Color by status
        StrategyStatut st = p.s.getStatut();
        if (st == StrategyStatut.ACCEPTEE) node.getStyleClass().add("dot-accepted");
        else if (st == StrategyStatut.EN_COURS) node.getStyleClass().add("dot-progress");
        else if (st == StrategyStatut.EN_ATTENTE) node.getStyleClass().add("dot-pending");
        else if (st == StrategyStatut.REFUSEE) node.getStyleClass().add("dot-refused");
        else node.getStyleClass().add("dot-default");

        // Bubble size by budget (clamped)
        double budget = Math.max(0, p.budget);
        // adjust divisor based on your DT scale
        double scale = 1.0 + (budget / 200_000.0);
        scale = Math.max(1.0, Math.min(2.6, scale));
        node.setScaleX(scale);
        node.setScaleY(scale);
    }

    private void installTooltip(Node node, Point p) {
        Tooltip tip = new Tooltip(
                safe(p.s.getNomStrategie()) +
                        "\nProjet: " + (p.s.getProjet() == null ? "-" : safe(p.s.getProjet().getTitleProj())) +
                        "\nROI: " + String.format(Locale.US, "%.1f", p.roi) + "%" +
                        "\nRisk: " + p.risk +
                        "\nBudget: " + String.format(Locale.US, "%,.0f DT", p.budget)
        );
        tip.setShowDelay(Duration.millis(120));
        Tooltip.install(node, tip);
    }

    private void installInteractions(Node node, Point p) {
        node.setOnMouseEntered(e -> {
            node.setStyle(node.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.4), 12, 0.5, 0, 0);");
        });

        node.setOnMouseExited(e -> {
            node.setStyle(node.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.4), 12, 0.5, 0, 0);", ""));
        });

        node.setOnMouseClicked(e -> onStrategyClicked.accept(p.s));
    }

    private void playAppearAnim(Node node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(220), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void playHoverIn(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
        st.setToX(node.getScaleX() * 1.10);
        st.setToY(node.getScaleY() * 1.10);
        st.play();
    }

    private void playHoverOut(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
        // return near original by dividing hover factor
        st.setToX(node.getScaleX() / 1.10);
        st.setToY(node.getScaleY() / 1.10);
        st.play();
    }

    private int computeRiskScore(int strategieId) {
        List<SWOTItem> items = swotService.getByStrategie(strategieId);
        int sum = 0;
        for (SWOTItem it : items) {
            if (it.getType() == SWOTType.WEAKNESS || it.getType() == SWOTType.THREAT) {
                Integer w = it.getWeight();
                sum += (w == null ? 1 : w);
            }
        }
        return sum;
    }

    private double computeRoiPercent(Strategie s) {
        if (s == null) return 0;
        if (s.getBudgetTotal() == 0 || s.getGainEstime() == 0) return 0;
        return strategieService.CalculROI(s.getGainEstime(), s.getBudgetTotal()) * 100.0;
    }

    @FXML
    private void onClose() { onClose.run(); }

    private String safe(String v) { return v == null ? "-" : v.trim(); }

    private static class Point {
        final Strategie s;
        final int risk;
        final double roi;
        final double budget;
        Point(Strategie s, int risk, double roi, double budget) {
            this.s = s; this.risk = risk; this.roi = roi; this.budget = budget;
        }
    }
}