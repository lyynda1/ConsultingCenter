package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Model.ressource.Ressource;
import com.advisora.Services.ressource.CatalogueFournisseurService;
import com.advisora.Services.ressource.ResourceAdminAiCopilotService;
import com.advisora.Services.ressource.ResourceAdminAiCopilotService.AdminActionItem;
import com.advisora.Services.ressource.ResourceAdminAiCopilotService.AdminAnalysisResult;
import com.advisora.Services.ressource.ResourceCatalogPdfExportService;
import com.advisora.Services.ressource.RessourceService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.RessourceStatut;
import com.advisora.enums.UserRole;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResourceListController {
    private static final String ALL_FILTER = "Tous";

    @FXML private ComboBox<CatalogueFournisseur> comboSupplier;
    @FXML private ComboBox<String> comboStatusFilter;
    @FXML private ComboBox<String> comboPriceFilter;
    @FXML private ComboBox<String> comboStockFilter;
    @FXML private TextField txtSearchResources;
    @FXML private HBox filterPanel;
    @FXML private ListView<Ressource> listResources;
    @FXML private Label lblStatus;
    @FXML private Label lblTotalResources;
    @FXML private Label lblTotalStock;
    @FXML private Label lblAvailableResources;

    private final CatalogueFournisseurService fournisseurService = new CatalogueFournisseurService();
    private final RessourceService service = new RessourceService();
    private final ResourceAdminAiCopilotService adminAiCopilotService = new ResourceAdminAiCopilotService();
    private final ResourceCatalogPdfExportService pdfExportService = new ResourceCatalogPdfExportService();
    private final ObservableList<Ressource> allData = FXCollections.observableArrayList();
    private final ObservableList<Ressource> data = FXCollections.observableArrayList();
    private final Map<Integer, String> supplierNameById = new HashMap<>();
    private Map<Integer, String> projectKeywordsByResourceId = Collections.emptyMap();

    @FXML
    public void initialize() {
        try {
            listResources.setItems(data);
            listResources.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Ressource item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildCard(item));
                }
            });

            comboSupplier.setItems(FXCollections.observableArrayList(fournisseurService.afficher()));
            rebuildSupplierNameMap();
            comboSupplier.valueProperty().addListener((obs, oldV, v) -> applySearchFilter());
            if (comboStatusFilter != null) {
                comboStatusFilter.setItems(FXCollections.observableArrayList(
                        ALL_FILTER,
                        RessourceStatut.AVAILABLE.name(),
                        RessourceStatut.RESERVED.name(),
                        RessourceStatut.UNAVAILABLE.name()
                ));
                comboStatusFilter.setValue(ALL_FILTER);
                comboStatusFilter.valueProperty().addListener((obs, oldV, newV) -> applySearchFilter());
            }
            if (comboPriceFilter != null) {
                comboPriceFilter.setItems(FXCollections.observableArrayList(
                        ALL_FILTER,
                        "<= 100",
                        "100 - 500",
                        "> 500"
                ));
                comboPriceFilter.setValue(ALL_FILTER);
                comboPriceFilter.valueProperty().addListener((obs, oldV, newV) -> applySearchFilter());
            }
            if (comboStockFilter != null) {
                comboStockFilter.setItems(FXCollections.observableArrayList(
                        ALL_FILTER,
                        "<= 10",
                        "11 - 50",
                        "> 50"
                ));
                comboStockFilter.setValue(ALL_FILTER);
                comboStockFilter.valueProperty().addListener((obs, oldV, newV) -> applySearchFilter());
            }
            if (txtSearchResources != null) {
                txtSearchResources.textProperty().addListener((obs, oldV, newV) -> applySearchFilter());
            }
            refresh();
        } catch (Exception ex) {
            lblStatus.setText("Erreur chargement ressources: " + ex.getMessage());
        }
    }

    @FXML
    private void onAdd() {
        try {
            authorizeManage();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/ResourceForm.fxml"));
            Parent root = loader.load();
            ResourceFormController controller = loader.getController();
            controller.initForCreate(selectedSupplierIdOrZero());
            openModal(root, "Ajouter ressource");
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onEdit() {
        try {
            authorizeManage();
            Ressource selected = requireSelection();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/ResourceForm.fxml"));
            Parent root = loader.load();
            ResourceFormController controller = loader.getController();
            controller.initForEdit(selected);
            openModal(root, "Modifier ressource");
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        try {
            authorizeManage();
            Ressource selected = requireSelection();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer cette ressource ?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            service.supprimer(selected);
            lblStatus.setText("Ressource supprimee #" + selected.getIdRs());
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onFilterOption() {
        if (filterPanel == null) {
            return;
        }
        filterPanel.setVisible(true);
        filterPanel.setManaged(true);
        if (txtSearchResources != null) {
            txtSearchResources.requestFocus();
            txtSearchResources.selectAll();
        }
        lblStatus.setText("Mode filtre actif.");
    }

    @FXML
    private void onHideFilters() {
        if (filterPanel == null) {
            return;
        }
        filterPanel.setVisible(false);
        filterPanel.setManaged(false);
        lblStatus.setText("Filtres masques.");
    }

    @FXML
    private void onResetFilters() {
        if (comboSupplier != null) {
            comboSupplier.getSelectionModel().clearSelection();
        }
        if (comboStatusFilter != null) {
            comboStatusFilter.setValue(ALL_FILTER);
        }
        if (comboPriceFilter != null) {
            comboPriceFilter.setValue(ALL_FILTER);
        }
        if (comboStockFilter != null) {
            comboStockFilter.setValue(ALL_FILTER);
        }
        if (txtSearchResources != null) {
            txtSearchResources.clear();
        }
        if (filterPanel != null) {
            filterPanel.setVisible(true);
            filterPanel.setManaged(true);
        }
        refresh();
        lblStatus.setText("Filtres reinitialises.");
    }

    @FXML
    private void onExportPdf() {
        try {
            authorizeManage();
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter liste ressources (PDF)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("resource_list_"
                    + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".pdf");

            File selected = chooser.showSaveDialog(lblStatus.getScene().getWindow());
            if (selected == null) {
                return;
            }
            File out = selected.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? selected
                    : (selected.getParentFile() == null
                    ? new File(selected.getName() + ".pdf")
                    : new File(selected.getParentFile(), selected.getName() + ".pdf"));

            List<Integer> ids = data.stream().map(Ressource::getIdRs).toList();
            Map<Integer, Integer> reservedById = service.getReservedStockBulk(ids);
            Map<Integer, Integer> availableById = service.getAvailableStockBulk(data, reservedById);
            List<ResourceCatalogPdfExportService.CatalogPdfRow> rows = data.stream()
                    .map(r -> new ResourceCatalogPdfExportService.CatalogPdfRow(
                            r.getIdRs(),
                            safe(r.getNomRs()),
                            supplierLabelFor(r.getIdFr()),
                            r.getPrixRs(),
                            r.getQuantiteRs(),
                            reservedById.getOrDefault(r.getIdRs(), 0),
                            availableById.getOrDefault(r.getIdRs(), 0),
                            r.getAvailabilityStatusRs() == null ? "-" : r.getAvailabilityStatusRs().name()))
                    .toList();

            String supplierFilter = comboSupplier.getValue() == null
                    ? "TOUS"
                    : supplierLabelFor(comboSupplier.getValue().getIdFr());
            String searchText = txtSearchResources == null ? "" : txtSearchResources.getText();

            pdfExportService.exportCatalogReport(rows, supplierFilter, searchText, out);
            ensureValidPdf(out);
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "PDF genere:\n" + out.getAbsolutePath(), ButtonType.OK);
            ok.setHeaderText("Export ressources termine");
            ok.showAndWait();
            lblStatus.setText("Export PDF termine.");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/ReservationList.fxml"));
            Parent root = loader.load();
            openModal(root, "Historique reservations");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onStatistics() {
        try {
            authorizeManage();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/ResourceStatisticsView.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Statistiques ressources");
            stage.setScene(new Scene(root));
            stage.setMinWidth(1250);
            stage.setMinHeight(780);
            stage.setWidth(1320);
            stage.setHeight(840);
            stage.showAndWait();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onAnalyzeAi() {
        try {
            authorizeManage();
            List<Ressource> scope = data.isEmpty() ? allData : data;
            if (scope.isEmpty()) {
                throw new IllegalStateException("Aucune ressource a analyser.");
            }
            Task<AdminAnalysisResult> task = new Task<>() {
                @Override
                protected AdminAnalysisResult call() {
                    List<Integer> ids = scope.stream().map(Ressource::getIdRs).toList();
                    Map<Integer, Integer> reservedById = service.getReservedStockBulk(ids);
                    return adminAiCopilotService.analyzeForAdmin(scope, supplierNameById, reservedById);
                }
            };

            task.setOnRunning(e -> lblStatus.setText("Analyse IA en cours..."));
            task.setOnSucceeded(e -> {
                AdminAnalysisResult result = task.getValue();
                showAdminAiDecisionWindow(result);
                lblStatus.setText("Analyse IA terminee.");
            });
            task.setOnFailed(e -> {
                Throwable err = task.getException();
                showError(err == null ? "Analyse IA echouee." : err.getMessage());
                lblStatus.setText("Analyse IA echouee.");
            });

            Thread worker = new Thread(task, "resource-admin-ai-copilot");
            worker.setDaemon(true);
            worker.start();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void showAdminAiDecisionWindow(AdminAnalysisResult result) {
        if (result == null) {
            throw new IllegalStateException("Resultat IA vide.");
        }
        ObservableList<AdminActionItem> rows = FXCollections.observableArrayList(
                result.getActions()
        );
        FilteredList<AdminActionItem> filteredRows = new FilteredList<>(rows, item -> true);

        Label title = new Label("Copilote Admin IA - Analyse Decisionnelle");
        title.getStyleClass().add("module-title");
        Label subtitle = new Label("Version " + safe(result.getAnalysisVersion())
                + " | Genere le " + safe(result.getGeneratedAt())
                + " | Source: " + safe(result.getSource()));
        subtitle.getStyleClass().add("hint");

        VBox kpiResources = buildAiKpiCard("Ressources analysees", String.valueOf(result.getTotalResources()));
        VBox kpiStock = buildAiKpiCard("Stock critique", String.valueOf(result.getLowStockCount()));
        VBox kpiPrice = buildAiKpiCard("Prix anormal", String.valueOf(result.getPriceAnomalyCount()));
        VBox kpiSupplier = buildAiKpiCard("Fournisseur risque", String.valueOf(result.getSupplierRiskCount()));

        HBox kpiRow = new HBox(10, kpiResources, kpiStock, kpiPrice, kpiSupplier);
        kpiRow.getStyleClass().add("ai-kpi-row");

        ComboBox<String> priorityFilter = new ComboBox<>(FXCollections.observableArrayList(
                "TOUTES", "HAUTE", "MOYENNE", "BASSE"
        ));
        priorityFilter.setValue("TOUTES");
        priorityFilter.setOnAction(e -> {
            String p = priorityFilter.getValue();
            if (p == null || "TOUTES".equalsIgnoreCase(p)) {
                filteredRows.setPredicate(item -> true);
            } else {
                filteredRows.setPredicate(item -> p.equalsIgnoreCase(safe(item.getPriority())));
            }
        });

        Label filterLabel = new Label("Filtre priorite");
        filterLabel.getStyleClass().add("hint");
        HBox filterBar = new HBox(10, filterLabel, priorityFilter);
        filterBar.getStyleClass().add("ai-filter-row");

        TableView<AdminActionItem> table = buildAdminAiTable();
        table.setItems(filteredRows);
        table.setPlaceholder(new Label("Aucune action pour ce filtre."));
        VBox.setVgrow(table, Priority.ALWAYS);

        TextArea summaryArea = new TextArea(safe(result.getAiSummary()));
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        summaryArea.setPrefRowCount(5);
        summaryArea.getStyleClass().add("ai-summary-area");

        Label summaryTitle = new Label("Synthese IA");
        summaryTitle.getStyleClass().add("module-subtitle");
        VBox summaryBox = new VBox(6, summaryTitle, summaryArea);
        summaryBox.getStyleClass().add("surface-panel");

        Button btnExportCsv = new Button("Export CSV");
        btnExportCsv.getStyleClass().add("btn-ghost");
        btnExportCsv.setOnAction(e -> exportAdminAiCsv(new ArrayList<>(filteredRows), result));

        Button btnExportPdf = new Button("Export PDF");
        btnExportPdf.getStyleClass().add("btn-ghost");
        btnExportPdf.setOnAction(e -> exportAdminAiPdf(new ArrayList<>(filteredRows), result));

        Button btnClose = new Button("Fermer");
        btnClose.getStyleClass().add("btn-primary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, btnExportCsv, btnExportPdf, spacer, btnClose);
        footer.getStyleClass().add("ai-footer-row");

        VBox root = new VBox(10, title, subtitle, kpiRow, filterBar, table, summaryBox, footer);
        root.getStyleClass().add("module-root");
        root.setStyle("-fx-padding: 14;");
        root.getStylesheets().add(getClass().getResource("/views/style/resource-module.css").toExternalForm());

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Copilote Admin IA");
        Scene scene = new Scene(root, 1260, 780);
        stage.setScene(scene);
        btnClose.setOnAction(e -> stage.close());
        stage.showAndWait();
    }

    private TableView<AdminActionItem> buildAdminAiTable() {
        TableView<AdminActionItem> table = new TableView<>();
        table.getStyleClass().add("ai-table");

        TableColumn<AdminActionItem, String> colPriority = new TableColumn<>("Priorite");
        colPriority.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getPriority())));
        colPriority.setPrefWidth(110);
        colPriority.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("ai-priority-high", "ai-priority-medium", "ai-priority-low");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String p = item.toUpperCase(Locale.ROOT);
                setText(priorityIcon(p) + " " + p);
                getStyleClass().add(priorityStyleClass(p));
            }
        });

        TableColumn<AdminActionItem, String> colCode = new TableColumn<>("Code Action");
        colCode.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getActionCode())));
        colCode.setPrefWidth(130);

        TableColumn<AdminActionItem, String> colAction = new TableColumn<>("Action Recommandee");
        colAction.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getActionLabel())));
        colAction.setPrefWidth(240);

        TableColumn<AdminActionItem, String> colIndicator = new TableColumn<>("Indicateur");
        colIndicator.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getIndicator())));
        colIndicator.setPrefWidth(150);

        TableColumn<AdminActionItem, String> colImpact = new TableColumn<>("Impact Metier");
        colImpact.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getBusinessImpact())));
        colImpact.setPrefWidth(180);

        TableColumn<AdminActionItem, String> colJustification = new TableColumn<>("Justification");
        colJustification.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getJustification())));
        colJustification.setPrefWidth(340);
        colJustification.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setWrapText(true);
                setText(item);
            }
        });

        TableColumn<AdminActionItem, String> colConfidence = new TableColumn<>("Confiance");
        colConfidence.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getConfidencePct() + "%"));
        colConfidence.setPrefWidth(100);

        TableColumn<AdminActionItem, String> colDeadline = new TableColumn<>("Delai");
        colDeadline.setCellValueFactory(c -> new SimpleStringProperty(safe(c.getValue().getDeadline())));
        colDeadline.setPrefWidth(80);

        TableColumn<AdminActionItem, String> colStatus = new TableColumn<>("Statut");
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(defaultDecisionStatus(c.getValue())));
        colStatus.setPrefWidth(120);

        table.getColumns().setAll(
                colPriority, colCode, colAction, colIndicator, colImpact, colJustification, colConfidence, colDeadline, colStatus
        );
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private VBox buildAiKpiCard(String title, String value) {
        Label kpiTitle = new Label(safe(title));
        kpiTitle.getStyleClass().add("stat-label");
        Label kpiValue = new Label(safe(value));
        kpiValue.getStyleClass().add("stat-value");

        VBox box = new VBox(6, kpiTitle, kpiValue);
        box.getStyleClass().addAll("stat-card", "ai-kpi-card");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void exportAdminAiCsv(List<AdminActionItem> rows, AdminAnalysisResult result) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter analyse IA (CSV)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            chooser.setInitialFileName("resource_admin_ai_"
                    + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".csv");
            File selected = chooser.showSaveDialog(lblStatus.getScene().getWindow());
            if (selected == null) {
                return;
            }
            File out = selected.getName().toLowerCase(Locale.ROOT).endsWith(".csv")
                    ? selected
                    : (selected.getParentFile() == null
                    ? new File(selected.getName() + ".csv")
                    : new File(selected.getParentFile(), selected.getName() + ".csv"));

            try (BufferedWriter writer = new BufferedWriter(new java.io.OutputStreamWriter(
                    new FileOutputStream(out), StandardCharsets.UTF_8))) {
                writer.write("analysis_version,generated_at,source,total_resources,low_stock,overstock,price_anomaly,supplier_risk");
                writer.newLine();
                writer.write(csv(result.getAnalysisVersion()) + ","
                        + csv(result.getGeneratedAt()) + ","
                        + csv(result.getSource()) + ","
                        + result.getTotalResources() + ","
                        + result.getLowStockCount() + ","
                        + result.getOverstockCount() + ","
                        + result.getPriceAnomalyCount() + ","
                        + result.getSupplierRiskCount());
                writer.newLine();
                writer.newLine();
                writer.write("priority,action_code,action_label,indicator,business_impact,justification,confidence_pct,deadline,status");
                writer.newLine();
                for (AdminActionItem row : rows) {
                    writer.write(csv(row.getPriority()) + ","
                            + csv(row.getActionCode()) + ","
                            + csv(row.getActionLabel()) + ","
                            + csv(row.getIndicator()) + ","
                            + csv(row.getBusinessImpact()) + ","
                            + csv(row.getJustification()) + ","
                            + row.getConfidencePct() + ","
                            + csv(row.getDeadline()) + ","
                            + csv(defaultDecisionStatus(row)));
                    writer.newLine();
                }
            }
            lblStatus.setText("Export CSV analyse IA termine.");
        } catch (Exception ex) {
            showError("Export CSV IA: " + ex.getMessage());
        }
    }

    private void exportAdminAiPdf(List<AdminActionItem> rows, AdminAnalysisResult result) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter analyse IA (PDF)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("resource_admin_ai_"
                    + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".pdf");
            File selected = chooser.showSaveDialog(lblStatus.getScene().getWindow());
            if (selected == null) {
                return;
            }
            File out = selected.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? selected
                    : (selected.getParentFile() == null
                    ? new File(selected.getName() + ".pdf")
                    : new File(selected.getParentFile(), selected.getName() + ".pdf"));

            Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
                PdfWriter.getInstance(doc, bos);
                doc.open();

                Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f);
                Font body = FontFactory.getFont(FontFactory.HELVETICA, 10f);
                Font summary = FontFactory.getFont(FontFactory.HELVETICA, 9f);

                doc.add(new Paragraph("Copilote Admin IA - Analyse Decisionnelle", h1));
                doc.add(new Paragraph("Version: " + safe(result.getAnalysisVersion())
                        + " | Date: " + safe(result.getGeneratedAt())
                        + " | Source: " + safe(result.getSource()), body));
                doc.add(new Paragraph("Ressources: " + result.getTotalResources()
                        + " | Stock critique: " + result.getLowStockCount()
                        + " | Surstock: " + result.getOverstockCount()
                        + " | Prix anormal: " + result.getPriceAnomalyCount()
                        + " | Fournisseur risque: " + result.getSupplierRiskCount(), body));
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("Synthese IA: " + safe(result.getAiSummary()), summary));
                doc.add(new Paragraph(" "));

                PdfPTable table = new PdfPTable(new float[]{1.1f, 1.5f, 2.2f, 1.7f, 2.0f, 3.5f, 1.0f, 0.8f, 1.2f});
                table.setWidthPercentage(100f);
                table.addCell(pdfHeader("Priorite"));
                table.addCell(pdfHeader("Code"));
                table.addCell(pdfHeader("Action"));
                table.addCell(pdfHeader("Indicateur"));
                table.addCell(pdfHeader("Impact"));
                table.addCell(pdfHeader("Justification"));
                table.addCell(pdfHeader("Confiance"));
                table.addCell(pdfHeader("Delai"));
                table.addCell(pdfHeader("Statut"));

                for (AdminActionItem row : rows) {
                    table.addCell(pdfBody(safe(row.getPriority())));
                    table.addCell(pdfBody(safe(row.getActionCode())));
                    table.addCell(pdfBody(safe(row.getActionLabel())));
                    table.addCell(pdfBody(safe(row.getIndicator())));
                    table.addCell(pdfBody(safe(row.getBusinessImpact())));
                    table.addCell(pdfBody(safe(row.getJustification())));
                    table.addCell(pdfBody(row.getConfidencePct() + "%"));
                    table.addCell(pdfBody(safe(row.getDeadline())));
                    table.addCell(pdfBody(defaultDecisionStatus(row)));
                }

                doc.add(table);
            } finally {
                if (doc.isOpen()) {
                    doc.close();
                }
            }
            ensureValidPdf(out);
            lblStatus.setText("Export PDF analyse IA termine.");
        } catch (Exception ex) {
            showError("Export PDF IA: " + ex.getMessage());
        }
    }

    private PdfPCell pdfHeader(String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setBackgroundColor(new Color(76, 114, 176));
        cell.setPadding(5f);
        return cell;
    }

    private PdfPCell pdfBody(String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.BLACK);
        PdfPCell cell = new PdfPCell(new Phrase(safe(text), font));
        cell.setPadding(4f);
        return cell;
    }

    private String priorityStyleClass(String priority) {
        if ("HAUTE".equalsIgnoreCase(priority)) {
            return "ai-priority-high";
        }
        if ("MOYENNE".equalsIgnoreCase(priority)) {
            return "ai-priority-medium";
        }
        return "ai-priority-low";
    }

    private String priorityIcon(String priority) {
        if ("HAUTE".equalsIgnoreCase(priority)) {
            return "●";
        }
        if ("MOYENNE".equalsIgnoreCase(priority)) {
            return "◑";
        }
        return "○";
    }

    private String defaultDecisionStatus(AdminActionItem item) {
        if (item == null) {
            return "A TRAITER";
        }
        String p = safe(item.getPriority());
        if ("HAUTE".equalsIgnoreCase(p)) {
            return "A TRAITER";
        }
        if ("MOYENNE".equalsIgnoreCase(p)) {
            return "PLANIFIER";
        }
        return "SURVEILLER";
    }

    private String csv(String value) {
        String raw = value == null ? "" : value;
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private void refresh() {
        List<Ressource> list = service.afficher();
        allData.setAll(list);
        List<Integer> ids = list.stream().map(Ressource::getIdRs).toList();
        projectKeywordsByResourceId = service.getProjectTitlesByResourceIds(ids);
        applySearchFilter();
    }

    private void applySearchFilter() {
        String query = txtSearchResources == null ? "" : txtSearchResources.getText();
        String statusFilter = comboStatusFilter == null ? ALL_FILTER : comboStatusFilter.getValue();
        String priceFilter = comboPriceFilter == null ? ALL_FILTER : comboPriceFilter.getValue();
        String stockFilter = comboStockFilter == null ? ALL_FILTER : comboStockFilter.getValue();
        CatalogueFournisseur selectedSupplier = comboSupplier == null ? null : comboSupplier.getValue();
        List<Ressource> filtered = allData.stream()
                .filter(r -> matchesSupplierFilter(r, selectedSupplier))
                .filter(r -> matchesStatusFilter(r, statusFilter))
                .filter(r -> matchesPriceFilter(r, priceFilter))
                .filter(r -> matchesStockFilter(r, stockFilter))
                .filter(r -> matchesSearch(r, query))
                .toList();

        data.setAll(filtered);
        updateStats(filtered);

        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            lblStatus.setText("Filtre actif: " + filtered.size() + " / " + allData.size());
        } else {
            lblStatus.setText("Ressources chargees: " + filtered.size());
        }
    }

    private boolean matchesSupplierFilter(Ressource r, CatalogueFournisseur selectedSupplier) {
        if (r == null) {
            return false;
        }
        if (selectedSupplier == null || selectedSupplier.getIdFr() <= 0) {
            return true;
        }
        return r.getIdFr() == selectedSupplier.getIdFr();
    }

    private boolean matchesStatusFilter(Ressource r, String statusFilter) {
        if (r == null) {
            return false;
        }
        if (statusFilter == null || statusFilter.isBlank() || ALL_FILTER.equals(statusFilter)) {
            return true;
        }
        RessourceStatut s = r.getAvailabilityStatusRs();
        return s != null && s.name().equalsIgnoreCase(statusFilter);
    }

    private boolean matchesPriceFilter(Ressource r, String priceFilter) {
        if (r == null) {
            return false;
        }
        if (priceFilter == null || priceFilter.isBlank() || ALL_FILTER.equals(priceFilter)) {
            return true;
        }
        double price = r.getPrixRs();
        return switch (priceFilter) {
            case "<= 100" -> price <= 100.0;
            case "100 - 500" -> price > 100.0 && price <= 500.0;
            case "> 500" -> price > 500.0;
            default -> true;
        };
    }

    private boolean matchesStockFilter(Ressource r, String stockFilter) {
        if (r == null) {
            return false;
        }
        if (stockFilter == null || stockFilter.isBlank() || ALL_FILTER.equals(stockFilter)) {
            return true;
        }
        int stock = r.getQuantiteRs();
        return switch (stockFilter) {
            case "<= 10" -> stock <= 10;
            case "11 - 50" -> stock >= 11 && stock <= 50;
            case "> 50" -> stock > 50;
            default -> true;
        };
    }

    private boolean matchesSearch(Ressource r, String query) {
        if (r == null) {
            return false;
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return true;
        }

        RessourceStatut statusEnum = r.getAvailabilityStatusRs();
        String status = statusEnum == null ? "" : statusEnum.name();
        String statusKeywords;
        if (statusEnum == RessourceStatut.AVAILABLE) {
            statusKeywords = "disponible available";
        } else if (statusEnum == RessourceStatut.UNAVAILABLE) {
            statusKeywords = "indisponible unavailable";
        } else {
            statusKeywords = "pending en_attente";
        }

        String hay = normalize(
                r.getIdRs() + " "
                        + safe(r.getNomRs()) + " "
                        + supplierLabelFor(r.getIdFr()) + " "
                        + status + " "
                        + statusKeywords + " "
                        + String.format(Locale.US, "%.2f", r.getPrixRs()) + " "
                        + r.getQuantiteRs() + " "
                        + projectKeywordsByResourceId.getOrDefault(r.getIdRs(), "")
        );

        String[] tokens = normalizedQuery.split("\\s+");
        for (String token : tokens) {
            if (!token.isBlank() && !hay.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private void rebuildSupplierNameMap() {
        supplierNameById.clear();
        if (comboSupplier == null || comboSupplier.getItems() == null) {
            return;
        }
        for (CatalogueFournisseur item : comboSupplier.getItems()) {
            if (item == null) {
                continue;
            }
            supplierNameById.put(item.getIdFr(), formatSupplierName(item));
        }
    }

    private String supplierLabelFor(int supplierId) {
        String value = supplierNameById.get(supplierId);
        if (value == null || value.isBlank()) {
            return "#" + supplierId;
        }
        return value;
    }

    private String formatSupplierName(CatalogueFournisseur supplier) {
        if (supplier == null) {
            return "-";
        }
        String nom = safe(supplier.getNomFr());
        String fournisseur = safe(supplier.getFournisseur());
        if (!"-".equals(fournisseur) && !fournisseur.equalsIgnoreCase(nom)) {
            return nom + " (" + fournisseur + ")";
        }
        return nom;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
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

    private void updateStats(List<Ressource> resources) {
        int total = resources == null ? 0 : resources.size();
        int stock = resources == null ? 0 : resources.stream().mapToInt(Ressource::getQuantiteRs).sum();
        long available = resources == null ? 0 : resources.stream()
                .filter(r -> r.getAvailabilityStatusRs() == RessourceStatut.AVAILABLE)
                .count();

        if (lblTotalResources != null) lblTotalResources.setText(String.valueOf(total));
        if (lblTotalStock != null) lblTotalStock.setText(String.valueOf(stock));
        if (lblAvailableResources != null) lblAvailableResources.setText(String.valueOf(available));
    }

    private VBox buildCard(Ressource r) {
        Label title = new Label("#" + r.getIdRs() + " - " + safe(r.getNomRs()));
        title.getStyleClass().add("card-title");

        Label badge = new Label(r.getAvailabilityStatusRs() == null ? "-" : r.getAvailabilityStatusRs().name());
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(statusClassFor(r.getAvailabilityStatusRs()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        Label meta1 = new Label("Prix: " + r.getPrixRs() + "   |   Quantite: " + r.getQuantiteRs());
        meta1.getStyleClass().add("card-meta");
        Label meta2 = new Label("Fournisseur: " + supplierLabelFor(r.getIdFr()));
        meta2.getStyleClass().add("card-meta");
        String projectKeywords = projectKeywordsByResourceId.getOrDefault(r.getIdRs(), "");
        Label meta3 = new Label("Projets lies: " + (projectKeywords.isBlank() ? "-" : projectKeywords));
        meta3.getStyleClass().add("card-meta");
        meta3.setWrapText(true);

        VBox card = new VBox(8, head, meta1, meta2, meta3);
        card.getStyleClass().add("resource-card");
        return card;
    }

    private String statusClassFor(RessourceStatut status) {
        if (status == RessourceStatut.AVAILABLE) return "status-accepted";
        if (status == RessourceStatut.UNAVAILABLE) return "status-refused";
        return "status-pending";
    }

    private Ressource requireSelection() {
        Ressource selected = listResources.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez une ressource.");
        }
        return selected;
    }

    private void authorizeManage() {
        UserRole r = SessionContext.getCurrentRole();
        if (r != UserRole.ADMIN && r != UserRole.GERANT) {
            throw new IllegalStateException("Acces refuse: ADMIN ou GERANT requis.");
        }
    }

    private int selectedSupplierIdOrZero() {
        CatalogueFournisseur selected = comboSupplier.getValue();
        return selected == null ? 0 : selected.getIdFr();
    }

    private void openModal(Parent root, String title) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Gestion ressources");
        a.showAndWait();
    }

    private String safe(String value) {
        return value == null ? "-" : value.trim();
    }
}
