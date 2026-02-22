package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.Booking;
import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Model.projet.Project;
import com.advisora.Model.ressource.Ressource;
import com.advisora.Services.ressource.CatalogueFournisseurService;
import com.advisora.Services.projet.ProjectService;
import com.advisora.Services.ressource.ReservationService;
import com.advisora.Services.ressource.RessourceService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.RessourceStatut;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CatalogController {
    @FXML private ComboBox<CatalogueFournisseur> comboSupplier;
    @FXML private ComboBox<Project> comboProject;
    @FXML private TextField txtQuantity;
    @FXML private ListView<Ressource> listResources;
    @FXML private Label lblTotalResources;
    @FXML private Label lblReservedStock;
    @FXML private Label lblAvailableStock;
    @FXML private Label lblStatus;

    @FXML private ListView<Booking> listReservations;
    @FXML private TextField txtUpdateQuantity;
    @FXML private Label lblReservationCount;
    @FXML private Label lblReservationQty;

    private final CatalogueFournisseurService fournisseurService = new CatalogueFournisseurService();
    private final RessourceService ressourceService = new RessourceService();
    private final ProjectService projectService = new ProjectService();
    private final ReservationService reservationService = new ReservationService();

    private final ObservableList<Ressource> resourcesData = FXCollections.observableArrayList();
    private final ObservableList<Booking> reservationData = FXCollections.observableArrayList();

    private Map<Integer, Integer> reservedStockByResourceId = Collections.emptyMap();
    private Map<Integer, Integer> availableStockByResourceId = Collections.emptyMap();

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

            listReservations.setItems(reservationData);
            listReservations.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Booking item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildReservationCard(item));
                }
            });

            comboSupplier.setItems(FXCollections.observableArrayList(fournisseurService.afficher()));
            comboSupplier.valueProperty().addListener((obs, oldV, v) -> refreshResources());

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
    private void onUpdateReservation() {
        try {
            authorizeClient();
            Booking selected = requireReservationSelection();
            int qty = parsePositiveQuantity(txtUpdateQuantity.getText(), "Nouvelle quantite invalide.");

            reservationService.updateReservationForClient(
                    SessionContext.getCurrentUserId(),
                    selected.getIdProj(),
                    selected.getIdRs(),
                    qty
            );
            lblStatus.setText("Reservation modifiee.");
            refreshAll();
        } catch (Exception ex) {
            showError("Reservation", ex.getMessage());
        }
    }

    @FXML
    private void onDeleteReservation() {
        try {
            authorizeClient();
            Booking selected = requireReservationSelection();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer cette reservation ?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }

            reservationService.deleteReservationForClient(SessionContext.getCurrentUserId(), selected.getIdProj(), selected.getIdRs());
            lblStatus.setText("Reservation supprimee.");
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
    private void onRefreshReservations() {
        refreshReservations();
    }

    private void refreshAll() {
        refreshResources();
        refreshReservations();
    }

    private void refreshResources() {
        CatalogueFournisseur supplier = comboSupplier.getValue();
        List<Ressource> resources = (supplier == null)
                ? ressourceService.afficher()
                : ressourceService.getByFournisseur(supplier.getIdFr());

        resourcesData.setAll(resources);

        List<Integer> ids = resources.stream().map(Ressource::getIdRs).toList();
        reservedStockByResourceId = ressourceService.getReservedStockBulk(ids);
        availableStockByResourceId = ressourceService.getAvailableStockBulk(resources, reservedStockByResourceId);

        updateResourceStats(resources);
    }

    private void refreshReservations() {
        List<Booking> bookings = reservationService.listClientReservations(SessionContext.getCurrentUserId());
        reservationData.setAll(bookings);
        updateReservationStats(bookings);
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

    private void updateReservationStats(List<Booking> bookings) {
        int count = bookings == null ? 0 : bookings.size();
        int qty = bookings == null ? 0 : bookings.stream().mapToInt(Booking::getQuantity).sum();
        lblReservationCount.setText(String.valueOf(count));
        lblReservationQty.setText(String.valueOf(qty));
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

        VBox card = new VBox(8, head, row1, row2);
        card.getStyleClass().add("resource-card");
        return card;
    }

    private VBox buildReservationCard(Booking b) {
        Label title = new Label("Projet: " + safe(b.getProjectTitle()));
        title.getStyleClass().add("card-title");

        Label badge = new Label("Qte: " + b.getQuantity());
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add("reservation-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        Label line1 = new Label("Ressource: " + safe(b.getResourceName()) + "   |   Fournisseur: " + safe(b.getFournisseurName()));
        line1.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, line1);
        card.getStyleClass().addAll("resource-card", "reservation-card");
        return card;
    }

    private String statusClassFor(RessourceStatut status) {
        if (status == RessourceStatut.AVAILABLE) return "status-accepted";
        if (status == RessourceStatut.UNAVAILABLE) return "status-refused";
        return "status-pending";
    }

    private Booking requireReservationSelection() {
        Booking selected = listReservations.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez une reservation.");
        }
        return selected;
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