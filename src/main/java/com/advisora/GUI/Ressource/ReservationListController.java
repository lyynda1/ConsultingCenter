package com.advisora.GUI.Ressource;

import com.advisora.Model.Booking;
import com.advisora.Services.ReservationService;
import com.advisora.Services.SessionContext;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ReservationListController {
    @FXML private ListView<Booking> listReservations;
    @FXML private TextField txtNewQuantity;
    @FXML private Label lblReservationCount;
    @FXML private Label lblReservedQty;
    @FXML private Label lblStatus;

    private final ReservationService service = new ReservationService();
    private final ObservableList<Booking> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        listReservations.setItems(data);
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
                setGraphic(buildCard(item));
            }
        });
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onUpdate() {
        try {
            Booking selected = requireSelection();
            int qty = parseQuantity(txtNewQuantity.getText());
            if (qty <= 0) {
                throw new IllegalArgumentException("La nouvelle quantite doit etre > 0.");
            }

            if (SessionContext.getCurrentRole() == UserRole.CLIENT) {
                service.updateReservationForClient(SessionContext.getCurrentUserId(), selected.getIdProj(), selected.getIdRs(), qty);
            } else {
                service.updateReservationAsManager(selected.getIdProj(), selected.getIdRs(), qty);
            }
            lblStatus.setText("Reservation modifiee.");
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        try {
            Booking selected = requireSelection();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer cette reservation ?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }

            if (SessionContext.getCurrentRole() == UserRole.CLIENT) {
                service.deleteReservationForClient(SessionContext.getCurrentUserId(), selected.getIdProj(), selected.getIdRs());
            } else {
                service.deleteReservationAsManager(selected.getIdProj(), selected.getIdRs());
            }
            lblStatus.setText("Reservation supprimee.");
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void refresh() {
        if (SessionContext.getCurrentRole() == UserRole.CLIENT) {
            data.setAll(service.listClientReservations(SessionContext.getCurrentUserId()));
            lblStatus.setText("Reservations client chargees.");
        } else {
            data.setAll(service.listAllReservations());
            lblStatus.setText("Reservations globales chargees.");
        }
        updateStats();
    }

    private void updateStats() {
        lblReservationCount.setText(String.valueOf(data.size()));
        int qty = data.stream().mapToInt(Booking::getQuantity).sum();
        lblReservedQty.setText(String.valueOf(qty));
    }

    private VBox buildCard(Booking b) {
        Label title = new Label("Projet: " + safe(b.getProjectTitle()));
        title.getStyleClass().add("card-title");

        Label qtyBadge = new Label("Qte: " + b.getQuantity());
        qtyBadge.getStyleClass().add("status-badge");
        qtyBadge.getStyleClass().add("status-pending");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, qtyBadge);

        Label line1 = new Label("Ressource: " + safe(b.getResourceName()) + "   |   Fournisseur: " + safe(b.getFournisseurName()));
        line1.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, line1);
        if (SessionContext.getCurrentRole() != UserRole.CLIENT) {
            Label line2 = new Label("Client: " + safe(b.getClientName()));
            line2.getStyleClass().add("card-meta");
            card.getChildren().add(line2);
        }
        card.getStyleClass().add("resource-card");
        return card;
    }

    private Booking requireSelection() {
        Booking selected = listReservations.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez une reservation.");
        }
        return selected;
    }

    private int parseQuantity(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Saisissez la nouvelle quantite.");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantite invalide.");
        }
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Reservations");
        a.showAndWait();
    }

    private String safe(String value) {
        return value == null ? "-" : value.trim();
    }
}
