package com.advisora.GUI.Ressource;

import com.advisora.utils.SceneThemeApplier;

import com.advisora.Model.projet.Project;
import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Model.ressource.Ressource;
import com.advisora.Services.projet.ProjectService;
import com.advisora.Services.ressource.CatalogueFournisseurService;
import com.advisora.Services.ressource.ResourceCatalogPdfExportService;
import com.advisora.Services.ressource.ReservationService;
import com.advisora.Services.ressource.RessourceService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.RessourceStatut;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CatalogController {
    private static final String ALL_FILTER = "Tous";

    @FXML private ComboBox<CatalogueFournisseur> comboSupplier;
    @FXML private ComboBox<String> comboStatusFilter;
    @FXML private ComboBox<String> comboPriceFilter;
    @FXML private ComboBox<String> comboStockFilter;
    @FXML private ComboBox<Project> comboProject;
    @FXML private TextField txtSearchCatalog;
    @FXML private TextField txtQuantity;
    @FXML private ListView<Ressource> listResources;
    @FXML private Label lblTotalResources;
    @FXML private Label lblReservedStock;
    @FXML private Label lblAvailableStock;
    @FXML private Label lblStatus;

    private final CatalogueFournisseurService fournisseurService = new CatalogueFournisseurService();
    private final RessourceService ressourceService = new RessourceService();
    private final ProjectService projectService = new ProjectService();
    private final ReservationService reservationService = new ReservationService();
    private final ResourceCatalogPdfExportService pdfExportService = new ResourceCatalogPdfExportService();
    private final ObservableList<Ressource> allResourcesData = FXCollections.observableArrayList();
    private final ObservableList<Ressource> resourcesData = FXCollections.observableArrayList();

    private Map<Integer, Integer> reservedStockByResourceId = Collections.emptyMap();
    private Map<Integer, Integer> availableStockByResourceId = Collections.emptyMap();
    private final Map<Integer, String> supplierNameById = new HashMap<>();

    @FXML
    public void initialize() {
        try {
            authorizeClient();

            listResources.setItems(resourcesData);
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
                    setGraphic(buildResourceCard(item));
                }
            });

            comboSupplier.setItems(FXCollections.observableArrayList(fournisseurService.afficher()));
            rebuildSupplierNameMap();
            comboSupplier.valueProperty().addListener((obs, oldV, v) -> applyCatalogSearchFilter());
            if (comboStatusFilter != null) {
                comboStatusFilter.setItems(FXCollections.observableArrayList(
                        ALL_FILTER,
                        RessourceStatut.AVAILABLE.name(),
                        RessourceStatut.RESERVED.name(),
                        RessourceStatut.UNAVAILABLE.name()
                ));
                comboStatusFilter.setValue(ALL_FILTER);
                comboStatusFilter.valueProperty().addListener((obs, oldV, newV) -> applyCatalogSearchFilter());
            }
            if (comboPriceFilter != null) {
                comboPriceFilter.setItems(FXCollections.observableArrayList(
                        ALL_FILTER,
                        "<= 100",
                        "100 - 500",
                        "> 500"
                ));
                comboPriceFilter.setValue(ALL_FILTER);
                comboPriceFilter.valueProperty().addListener((obs, oldV, newV) -> applyCatalogSearchFilter());
            }
            if (comboStockFilter != null) {
                comboStockFilter.setItems(FXCollections.observableArrayList(
                        ALL_FILTER,
                        "<= 10",
                        "11 - 50",
                        "> 50"
                ));
                comboStockFilter.setValue(ALL_FILTER);
                comboStockFilter.valueProperty().addListener((obs, oldV, newV) -> applyCatalogSearchFilter());
            }
            if (txtSearchCatalog != null) {
                txtSearchCatalog.textProperty().addListener((obs, oldV, newV) -> applyCatalogSearchFilter());
            }

            loadProjects();
            refreshAll();
        } catch (Exception ex) {
            lblStatus.setText("Erreur chargement catalogue: " + ex.getMessage());
        }
    }

    @FXML
    private void onReserve() {
        try {
            authorizeClient();
            Ressource selected = listResources.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new IllegalArgumentException("Selectionnez une ressource.");
            }

            int qty = parsePositiveQuantity(txtQuantity.getText(), "Quantite invalide.");
            Project project = comboProject.getValue();
            Integer projectId = project == null ? null : project.getIdProj();

            reservationService.reserveForClient(SessionContext.getCurrentUserId(), selected.getIdRs(), qty, projectId);
            lblStatus.setText("Reservation enregistree.");
            loadProjects();
            refreshAll();
        } catch (Exception ex) {
            showError("Reservation", ex.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        refreshAll();
    }

    @FXML
    private void onResetCatalogFilters() {
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
        if (txtSearchCatalog != null) {
            txtSearchCatalog.clear();
        }
        refreshAll();
        lblStatus.setText("Filtres catalogue reinitialises.");
    }

    @FXML
    private void onOpenReservations() {
        try {
            authorizeClient();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/ReservationList.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Mes reservations");
            SceneThemeApplier.setScene(stage, root);
            stage.showAndWait();

            refreshResources();
            lblStatus.setText("Page reservations fermee.");
        } catch (Exception ex) {
            showError("Reservations", ex.getMessage());
        }
    }

    @FXML
    private void onStatistics() {
        try {
            authorizeClient();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/ResourceStatisticsView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Statistiques ressources");
            SceneThemeApplier.setScene(stage, root);
            stage.setMinWidth(1250);
            stage.setMinHeight(780);
            stage.setWidth(1320);
            stage.setHeight(840);
            stage.showAndWait();
        } catch (Exception ex) {
            showError("Statistiques", ex.getMessage());
        }
    }

    @FXML
    private void onExportPdf() {
        try {
            authorizeClient();
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter catalogue ressources (PDF)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("catalogue_ressources_"
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

            List<ResourceCatalogPdfExportService.CatalogPdfRow> rows = resourcesData.stream()
                    .map(r -> new ResourceCatalogPdfExportService.CatalogPdfRow(
                            r.getIdRs(),
                            safe(r.getNomRs()),
                            supplierLabelFor(r.getIdFr()),
                            r.getPrixRs(),
                            r.getQuantiteRs(),
                            reservedStockByResourceId.getOrDefault(r.getIdRs(), 0),
                            availableStockByResourceId.getOrDefault(r.getIdRs(), 0),
                            r.getAvailabilityStatusRs() == null ? "-" : r.getAvailabilityStatusRs().name()))
                    .toList();

            CatalogueFournisseur selectedSupplier = comboSupplier == null ? null : comboSupplier.getValue();
            String supplierFilter = selectedSupplier == null ? "TOUS" : supplierLabelFor(selectedSupplier.getIdFr());
            String searchText = txtSearchCatalog == null ? "" : txtSearchCatalog.getText();

            pdfExportService.exportCatalogReport(rows, supplierFilter, searchText, out);
            ensureValidPdf(out);

            Alert ok = new Alert(Alert.AlertType.INFORMATION, "PDF genere:\n" + out.getAbsolutePath(), ButtonType.OK);
            ok.setHeaderText("Export catalogue termine");
            ok.showAndWait();
            lblStatus.setText("Export PDF catalogue termine.");
        } catch (Exception ex) {
            showError("Export PDF", ex.getMessage());
        }
    }

    @FXML
    private void onOpenMiniShop() {
        try {
            authorizeClient();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/MarketplaceView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Mini Shop C2C");
            SceneThemeApplier.setScene(stage, root);
            stage.showAndWait();

            refreshAll();
        } catch (Exception ex) {
            showError("Mini Shop", ex.getMessage());
        }
    }

    private void refreshAll() {
        refreshResources();
    }

    private void refreshResources() {
        List<Ressource> resources = ressourceService.afficher();

        allResourcesData.setAll(resources);

        List<Integer> ids = resources.stream().map(Ressource::getIdRs).toList();
        reservedStockByResourceId = ressourceService.getReservedStockBulk(ids);
        availableStockByResourceId = ressourceService.getAvailableStockBulk(resources, reservedStockByResourceId);

        applyCatalogSearchFilter();
    }

    private void loadProjects() {
        int clientId = SessionContext.getCurrentUserId();
        comboProject.setItems(FXCollections.observableArrayList(projectService.getByClient(clientId)));
        if (!comboProject.getItems().isEmpty() && comboProject.getValue() == null) {
            comboProject.getSelectionModel().selectFirst();
        }
    }

    private void updateResourceStats(List<Ressource> resources) {
        int totalResources = resources == null ? 0 : resources.size();
        int reserved = resources == null ? 0 : resources.stream()
                .mapToInt(r -> reservedStockByResourceId.getOrDefault(r.getIdRs(), 0))
                .sum();
        int available = resources == null ? 0 : resources.stream()
                .mapToInt(r -> availableStockByResourceId.getOrDefault(r.getIdRs(), 0))
                .sum();

        lblTotalResources.setText(String.valueOf(totalResources));
        lblReservedStock.setText(String.valueOf(reserved));
        lblAvailableStock.setText(String.valueOf(available));
    }

    private void applyCatalogSearchFilter() {
        String query = txtSearchCatalog == null ? "" : txtSearchCatalog.getText();
        String statusFilter = comboStatusFilter == null ? ALL_FILTER : comboStatusFilter.getValue();
        String priceFilter = comboPriceFilter == null ? ALL_FILTER : comboPriceFilter.getValue();
        String stockFilter = comboStockFilter == null ? ALL_FILTER : comboStockFilter.getValue();
        CatalogueFournisseur selectedSupplier = comboSupplier == null ? null : comboSupplier.getValue();
        List<Ressource> filtered = allResourcesData.stream()
                .filter(r -> matchesSupplierFilter(r, selectedSupplier))
                .filter(r -> matchesStatusFilter(r, statusFilter))
                .filter(r -> matchesPriceFilter(r, priceFilter))
                .filter(r -> matchesStockFilter(r, stockFilter))
                .filter(r -> matchesCatalogSearch(r, query))
                .toList();

        resourcesData.setAll(filtered);
        updateResourceStats(filtered);

        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            lblStatus.setText("Filtre dynamique actif: " + filtered.size() + " / " + allResourcesData.size());
        } else {
            lblStatus.setText("Catalogue charge: " + filtered.size() + " ressource(s).");
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
        int available = availableStockByResourceId.getOrDefault(r.getIdRs(), r.getQuantiteRs());
        return switch (stockFilter) {
            case "<= 10" -> available <= 10;
            case "11 - 50" -> available >= 11 && available <= 50;
            case "> 50" -> available > 50;
            default -> true;
        };
    }

    private boolean matchesCatalogSearch(Ressource r, String query) {
        if (r == null) {
            return false;
        }
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return true;
        }
        int reserved = reservedStockByResourceId.getOrDefault(r.getIdRs(), 0);
        int available = availableStockByResourceId.getOrDefault(r.getIdRs(), 0);
        String status = r.getAvailabilityStatusRs() == null ? "" : r.getAvailabilityStatusRs().name();
        String supplier = supplierLabelFor(r.getIdFr());

        String hay = normalize(
                r.getIdRs() + " "
                        + safe(r.getNomRs()) + " "
                        + supplier + " "
                        + status + " "
                        + String.format(Locale.US, "%.2f", r.getPrixRs()) + " "
                        + r.getQuantiteRs() + " "
                        + reserved + " "
                        + available
        );

        String[] tokens = normalized.split("\\s+");
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
            if (item != null) {
                supplierNameById.put(item.getIdFr(), formatSupplierName(item));
            }
        }
    }

    private String supplierLabelFor(int supplierId) {
        String label = supplierNameById.get(supplierId);
        if (label != null && !label.isBlank()) {
            return label;
        }
        return "#" + supplierId;
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

    private VBox buildResourceCard(Ressource r) {
        Label title = new Label("#" + r.getIdRs() + " - " + safe(r.getNomRs()));
        title.getStyleClass().add("card-title");

        Label badge = new Label(r.getAvailabilityStatusRs() == null ? "-" : r.getAvailabilityStatusRs().name());
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(statusClassFor(r.getAvailabilityStatusRs()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        int reserved = reservedStockByResourceId.getOrDefault(r.getIdRs(), 0);
        int available = availableStockByResourceId.getOrDefault(r.getIdRs(), 0);

        Label row1 = new Label("Prix: " + r.getPrixRs() + "   |   Stock total: " + r.getQuantiteRs());
        row1.getStyleClass().add("card-meta");
        Label row2 = new Label("Reserve: " + reserved + "   |   Disponible: " + available);
        row2.getStyleClass().add("card-meta");
        Label row3 = new Label("Fournisseur: " + supplierLabelFor(r.getIdFr()));
        row3.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, row1, row2, row3);
        card.getStyleClass().add("resource-card");
        return card;
    }

    private String statusClassFor(RessourceStatut status) {
        if (status == RessourceStatut.AVAILABLE) return "status-accepted";
        if (status == RessourceStatut.UNAVAILABLE) return "status-refused";
        return "status-pending";
    }

    private int parsePositiveQuantity(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            int qty = Integer.parseInt(value.trim());
            if (qty <= 0) {
                throw new IllegalArgumentException(message);
            }
            return qty;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private void authorizeClient() {
        if (SessionContext.getCurrentRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Seul le CLIENT peut utiliser cette vue.");
        }
    }

    private void showError(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private String safe(String value) {
        return value == null ? "-" : value.trim();
    }
}



