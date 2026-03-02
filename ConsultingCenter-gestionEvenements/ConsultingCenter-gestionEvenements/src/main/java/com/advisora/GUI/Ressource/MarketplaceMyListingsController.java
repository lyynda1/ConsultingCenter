package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.ResourceMarketListing;
import com.advisora.Services.ressource.ResourceMarketplaceService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Locale;

public class MarketplaceMyListingsController {
    @FXML private ListView<ResourceMarketListing> listMyListings;
    @FXML private Button btnCancelListing;
    @FXML private Label lblMyListingsCount;
    @FXML private Label lblCancelHint;
    @FXML private Label lblStatus;

    private final ResourceMarketplaceService marketplaceService = new ResourceMarketplaceService();
    private final ObservableList<ResourceMarketListing> myListingsData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        try {
            authorizeClient();
            listMyListings.setItems(myListingsData);
            listMyListings.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(ResourceMarketListing item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildMyListingCard(item));
                }
            });
            listMyListings.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updateCancelActionState(newV));
            refreshListings();
            updateCancelActionState(null);
        } catch (Exception ex) {
            if (lblStatus != null) {
                lblStatus.setText("Erreur chargement annonces: " + ex.getMessage());
            }
        }
    }

    @FXML
    private void onRefresh() {
        refreshListings();
    }

    @FXML
    private void onCancelListing() {
        try {
            authorizeClient();
            ResourceMarketListing selected = requireCancelableListingSelection();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Annuler cette annonce ?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            marketplaceService.cancelListing(SessionContext.getCurrentUserId(), selected.getIdListing());
            if (lblStatus != null) {
                lblStatus.setText("Annonce annulee.");
            }
            refreshListings();
            updateCancelActionState(listMyListings == null ? null : listMyListings.getSelectionModel().getSelectedItem());
        } catch (Exception ex) {
            showError("Mes annonces", ex.getMessage());
        }
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) (lblStatus != null ? lblStatus.getScene().getWindow() : null);
        if (stage != null) {
            stage.close();
        }
    }

    private void refreshListings() {
        List<ResourceMarketListing> myListings = marketplaceService.listMyListings(SessionContext.getCurrentUserId());
        myListingsData.setAll(myListings);
        updateCancelActionState(listMyListings == null ? null : listMyListings.getSelectionModel().getSelectedItem());
        if (lblMyListingsCount != null) {
            lblMyListingsCount.setText(String.valueOf(myListings.size()));
        }
    }

    private VBox buildMyListingCard(ResourceMarketListing listing) {
        Label title = new Label("#" + listing.getIdListing() + " - " + safe(listing.getResourceName()));
        title.getStyleClass().add("card-title");

        Label badge = new Label(normalizeStatusForUi(listing.getStatus()) + " | Qte dispo: " + listing.getQtyAvailable());
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(marketStatusClass(listing.getStatus()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        Label line1 = new Label("Prix: " + formatPrice(listing.getUnitPrice()) + " | Qte totale: " + listing.getQtyTotal());
        line1.getStyleClass().add("card-meta");
        Label line3 = new Label(renderStars(listing.getAverageStars()) + "  " + formatRating(listing));
        line3.getStyleClass().add("shop-stars");

        VBox card = new VBox(8, head, line1, line3);
        if (listing.getNote() != null && !listing.getNote().isBlank()) {
            Label note = new Label("Description: " + listing.getNote().trim());
            note.getStyleClass().add("card-meta");
            note.setWrapText(true);
            card.getChildren().add(note);
        }
        card.getStyleClass().add("resource-card");
        return card;
    }

    private ResourceMarketListing requireCancelableListingSelection() {
        ResourceMarketListing selected = listMyListings.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez une annonce.");
        }
        if (!isCancelable(selected)) {
            throw new IllegalStateException("Annulation indisponible: seule une annonce LISTED peut etre annulee.");
        }
        return selected;
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

    private String normalizeStatusForUi(String status) {
        if (status == null) {
            return "-";
        }
        if ("ACTIVE".equalsIgnoreCase(status) || "OPEN".equalsIgnoreCase(status) || "LISTED".equalsIgnoreCase(status)) {
            return "LISTED";
        }
        return status.toUpperCase(Locale.ROOT);
    }

    private String marketStatusClass(String status) {
        if (status == null) {
            return "status-pending";
        }
        if ("ACTIVE".equalsIgnoreCase(status)
                || "CONFIRMED".equalsIgnoreCase(status)
                || "OPEN".equalsIgnoreCase(status)
                || "LISTED".equalsIgnoreCase(status)) {
            return "status-accepted";
        }
        if ("SOLD_OUT".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            return "status-refused";
        }
        return "status-pending";
    }

    private String renderStars(double average) {
        int full = (int) Math.round(Math.max(0, Math.min(5, average)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < full ? '\u2605' : '\u2606');
        }
        return sb.toString();
    }

    private String formatRating(ResourceMarketListing listing) {
        if (listing.getReviewCount() <= 0) {
            return "Nouveau";
        }
        return String.format(Locale.ROOT, "%.1f (%d avis)", listing.getAverageStars(), listing.getReviewCount());
    }

    private String formatPrice(double price) {
        return String.format(Locale.ROOT, "%.2f", price);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isCancelable(ResourceMarketListing listing) {
        return listing != null
                && "LISTED".equalsIgnoreCase(safe(listing.getStatus()))
                && listing.getQtyAvailable() > 0;
    }

    private void updateCancelActionState(ResourceMarketListing selected) {
        if (btnCancelListing == null) {
            return;
        }
        boolean canCancel = isCancelable(selected);
        btnCancelListing.setDisable(!canCancel);

        if (lblCancelHint == null) {
            return;
        }
        if (selected == null) {
            lblCancelHint.setText("Selectionnez une annonce LISTED pour annuler.");
            return;
        }
        if (canCancel) {
            lblCancelHint.setText("");
            return;
        }
        lblCancelHint.setText("Annulation indisponible: statut " + normalizeStatusForUi(selected.getStatus()) + ".");
    }
}
