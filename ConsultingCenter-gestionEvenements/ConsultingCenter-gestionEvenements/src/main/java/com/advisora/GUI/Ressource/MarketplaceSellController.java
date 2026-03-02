package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.Booking;
import com.advisora.Model.ressource.Ressource;
import com.advisora.Services.ressource.ReservationService;
import com.advisora.Services.ressource.ResourceMarketplaceService;
import com.advisora.Services.ressource.RessourceService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

public class MarketplaceSellController {
    @FXML private ComboBox<Booking> comboSellResource;
    @FXML private Label lblSellReservationsCount;
    @FXML private TextField txtSellQuantity;
    @FXML private TextField txtSellPrice;
    @FXML private Label lblImageName;
    @FXML private Label lblSellGuide;
    @FXML private Label lblStatus;

    private final ReservationService reservationService = new ReservationService();
    private final ResourceMarketplaceService marketplaceService = new ResourceMarketplaceService();
    private final RessourceService ressourceService = new RessourceService();

    private final ObservableList<Booking> sellReservationsData = FXCollections.observableArrayList();
    private File selectedImageFile;

    @FXML
    public void initialize() {
        try {
            authorizeClient();

            comboSellResource.setItems(sellReservationsData);
            comboSellResource.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Booking item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : formatResourceOption(item));
                }
            });
            comboSellResource.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Booking item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : formatResourceOption(item));
                }
            });

            comboSellResource.valueProperty().addListener((obs, oldV, selected) -> {
                if (selected == null) {
                    if (lblSellGuide != null) {
                        lblSellGuide.setText("Selectionnez une ressource.");
                    }
                    return;
                }
                if (txtSellQuantity != null && (txtSellQuantity.getText() == null || txtSellQuantity.getText().isBlank())) {
                    txtSellQuantity.setText("1");
                }
                if (txtSellPrice != null && (txtSellPrice.getText() == null || txtSellPrice.getText().isBlank())) {
                    Ressource r = ressourceService.getById(selected.getIdRs());
                    if (r != null) {
                        txtSellPrice.setText(String.format(Locale.ROOT, "%.2f", r.getPrixRs()));
                    }
                }
                if (lblSellGuide != null) {
                    lblSellGuide.setText("Cliquez Publier.");
                }
            });

            refreshReservations();
        } catch (Exception ex) {
            if (lblStatus != null) {
                lblStatus.setText("Erreur chargement vente: " + ex.getMessage());
            }
        }
    }

    @FXML
    private void onRefresh() {
        refreshReservations();
    }

    @FXML
    private void onChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir une image produit");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        Stage stage = (Stage) (lblStatus != null ? lblStatus.getScene().getWindow() : null);
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        selectedImageFile = file;
        if (lblImageName != null) {
            lblImageName.setText(file.getName());
        }
    }

    @FXML
    private void onPublishListing() {
        try {
            authorizeClient();
            Booking selected = requireResourceSelection();
            int qty = parsePositiveQuantity(txtSellQuantity.getText(), "Quantite invalide.");
            double unitPrice = parsePositivePrice(txtSellPrice.getText(), "Prix invalide.");
            String imageUrl = saveSelectedImageAndResolveUrl();

            marketplaceService.publishListingFromReservation(
                    SessionContext.getCurrentUserId(),
                    selected.getIdProj(),
                    selected.getIdRs(),
                    qty,
                    unitPrice,
                    null,
                    imageUrl
            );
            if (lblStatus != null) {
                lblStatus.setText("Annonce publiee.");
            }
            clearInputs();
            refreshReservations();
        } catch (Exception ex) {
            showError("Publier annonce", ex.getMessage());
        }
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) (lblStatus != null ? lblStatus.getScene().getWindow() : null);
        if (stage != null) {
            stage.close();
        }
    }

    private void refreshReservations() {
        List<Booking> bookings = reservationService.listClientReservations(SessionContext.getCurrentUserId());
        sellReservationsData.setAll(bookings);
        if (!bookings.isEmpty() && comboSellResource.getValue() == null) {
            comboSellResource.getSelectionModel().selectFirst();
        }
        if (lblSellReservationsCount != null) {
            lblSellReservationsCount.setText(String.valueOf(bookings.size()));
        }
    }

    private Booking requireResourceSelection() {
        Booking selected = comboSellResource.getValue();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez une ressource.");
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

    private double parsePositivePrice(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            double price = Double.parseDouble(value.trim());
            if (price < 0) {
                throw new IllegalArgumentException(message);
            }
            return price;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private void clearInputs() {
        if (txtSellQuantity != null) {
            txtSellQuantity.clear();
        }
        if (txtSellPrice != null) {
            txtSellPrice.clear();
        }
        if (comboSellResource != null) {
            comboSellResource.getSelectionModel().clearSelection();
        }
        selectedImageFile = null;
        if (lblImageName != null) {
            lblImageName.setText("Aucune image");
        }
        if (lblSellGuide != null) {
            lblSellGuide.setText("Selectionnez une ressource.");
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

    private String formatResourceOption(Booking booking) {
        return safe(booking.getResourceName())
                + " | " + safe(booking.getFournisseurName())
                + " | Qte dispo: " + booking.getQuantity();
    }

    private String saveSelectedImageAndResolveUrl() {
        if (selectedImageFile == null) {
            return null;
        }
        try {
            String original = selectedImageFile.getName();
            String clean = original.replaceAll("[^A-Za-z0-9._-]", "_");
            Path dir = Paths.get("uploads", "marketplace");
            Files.createDirectories(dir);
            Path target = dir.resolve(System.currentTimeMillis() + "_" + clean);
            Files.copy(selectedImageFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath().toUri().toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible d'enregistrer l'image importee: " + ex.getMessage(), ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
