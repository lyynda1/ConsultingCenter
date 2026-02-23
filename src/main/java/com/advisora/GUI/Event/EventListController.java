package com.advisora.GUI.Event;

import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.transformation.FilteredList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EventListController {
    @FXML private ListView<Event> listEvents;
    @FXML private TextField txtSearch;
    @FXML private TextField txtReserveQty;
    @FXML private Button btnReserve;
    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Label lblStatus;
    @FXML private Label lblTotalEvents;
    @FXML private Label lblUpcomingEvents;
    @FXML private Label lblAvailableSeats;

    @FXML private ListView<EventBooking> listBookings;
    @FXML private Button btnCancelBooking;
    @FXML private Label lblBookingCount;
    @FXML private Label lblBookingSeats;
    @FXML private Label lblBookingTitle;

    private final EventService eventService = new EventService();
    private final EventBookingService bookingService = new EventBookingService();

    private final ObservableList<Event> allEvents = FXCollections.observableArrayList();
    private final FilteredList<Event> filteredEvents = new FilteredList<>(allEvents, e -> true);
    private final ObservableList<EventBooking> bookingsData = FXCollections.observableArrayList();

    private Map<Integer, Integer> reservedByEventId = new HashMap<>();
    private final DateTimeFormatter dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        try {
            UserRole role = SessionContext.getCurrentRole();
            boolean canManage = role == UserRole.ADMIN || role == UserRole.GERANT;

            btnAdd.setVisible(canManage);
            btnAdd.setManaged(canManage);
            btnEdit.setVisible(canManage);
            btnEdit.setManaged(canManage);
            btnDelete.setVisible(canManage);
            btnDelete.setManaged(canManage);

            boolean isClient = role == UserRole.CLIENT;
            txtReserveQty.setVisible(isClient);
            txtReserveQty.setManaged(isClient);
            btnReserve.setVisible(isClient);
            btnReserve.setManaged(isClient);

            if (lblBookingTitle != null) {
                lblBookingTitle.setText(isClient ? "Mes reservations" : "Toutes les reservations");
            }

            listEvents.setItems(filteredEvents);
            listEvents.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Event item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildEventCard(item));
                }
            });

            listBookings.setItems(bookingsData);
            listBookings.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(EventBooking item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildBookingCard(item));
                }
            });

            if (txtSearch != null) {
                txtSearch.textProperty().addListener((obs, oldV, newV) -> applyEventFilter(newV));
            }

            refreshAll();
        } catch (Exception ex) {
            if (lblStatus != null) {
                lblStatus.setText("Erreur chargement evenements: " + ex.getMessage());
            }
            ex.printStackTrace();
        }
    }

    @FXML
    private void onAdd() {
        try {
            authorizeManage();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/event/EventForm.fxml"));
            Parent root = loader.load();
            EventFormController controller = loader.getController();
            controller.initForCreate();
            openModal(root, "Ajouter evenement");
            refreshEvents();
        } catch (Exception ex) {
            showError("Evenements", ex.getMessage());
        }
    }

    @FXML
    private void onEdit() {
        try {
            authorizeManage();
            Event selected = requireEventSelection();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/event/EventForm.fxml"));
            Parent root = loader.load();
            EventFormController controller = loader.getController();
            controller.initForEdit(selected);
            openModal(root, "Modifier evenement");
            refreshEvents();
        } catch (Exception ex) {
            showError("Evenements", ex.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        try {
            authorizeManage();
            Event selected = requireEventSelection();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer cet evenement ?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            eventService.delete(selected.getIdEv());
            lblStatus.setText("Evenement supprime.");
            refreshAll();
        } catch (Exception ex) {
            showError("Evenements", ex.getMessage());
        }
    }

    @FXML
    private void onReserve() {
        try {
            authorizeClient();
            Event selected = requireEventSelection();
            int qty = parsePositiveInt(txtReserveQty.getText(), "Nombre de places invalide.");
            bookingService.createBooking(SessionContext.getCurrentUserId(), selected.getIdEv(), qty);
            lblStatus.setText("Reservation enregistree.");
            refreshAll();
        } catch (Exception ex) {
            showError("Reservation", ex.getMessage());
        }
    }

    @FXML
    private void onCancelBooking() {
        try {
            EventBooking selected = requireBookingSelection();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Annuler cette reservation ?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            if (SessionContext.getCurrentRole() == UserRole.CLIENT) {
                bookingService.cancelBookingForClient(SessionContext.getCurrentUserId(), selected.getIdBk());
            } else {
                bookingService.cancelBookingAsManager(selected.getIdBk());
            }
            lblStatus.setText("Reservation annulee.");
            refreshAll();
        } catch (Exception ex) {
            showError("Reservations", ex.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        refreshAll();
    }

    @FXML
    private void onRefreshBookings() {
        refreshBookings();
    }

    private void refreshAll() {
        refreshEvents();
        refreshBookings();
    }

    private void refreshEvents() {
        List<Event> events = eventService.getAll();
        allEvents.setAll(events);
        List<Integer> ids = events.stream().map(Event::getIdEv).collect(Collectors.toList());
        reservedByEventId = bookingService.getReservedSeatsByEventIds(ids);
        updateEventStats(filteredEvents);
    }

    private void applyEventFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        filteredEvents.setPredicate(e -> {
            if (q.isEmpty()) {
                return true;
            }
            if (e == null) {
                return false;
            }
            return containsIgnoreCase(e.getTitleEv(), q)
                    || containsIgnoreCase(e.getLocalisationEv(), q)
                    || containsIgnoreCase(e.getOrganisateurName(), q)
                    || containsIgnoreCase(fmtDateTime(e.getStartDateEv()), q)
                    || containsIgnoreCase(fmtDateTime(e.getEndDateEv()), q);
        });
        updateEventStats(filteredEvents);
    }

    private void refreshBookings() {
        if (SessionContext.getCurrentRole() == UserRole.CLIENT) {
            bookingsData.setAll(bookingService.listByClient(SessionContext.getCurrentUserId()));
        } else {
            bookingsData.setAll(bookingService.listAll());
        }
        updateBookingStats();
    }

    private void updateEventStats(List<Event> events) {
        int total = events == null ? 0 : events.size();
        int upcoming = 0;
        int available = 0;
        LocalDateTime now = LocalDateTime.now();
        if (events != null) {
            for (Event e : events) {
                if (e == null || e.getStartDateEv() == null) {
                    continue;
                }
                if (!e.getStartDateEv().isBefore(now)) {
                    upcoming++;
                }
                int reserved = reservedByEventId.getOrDefault(e.getIdEv(), 0);
                available += Math.max(0, e.getCapaciteEvnt() - reserved);
            }
        }
        lblTotalEvents.setText(String.valueOf(total));
        lblUpcomingEvents.setText(String.valueOf(upcoming));
        lblAvailableSeats.setText(String.valueOf(available));
    }

    private void updateBookingStats() {
        int count = bookingsData.size();
        int seats = bookingsData.stream().mapToInt(EventBooking::getNumTicketBk).sum();
        lblBookingCount.setText(String.valueOf(count));
        lblBookingSeats.setText(String.valueOf(seats));
    }

    private VBox buildEventCard(Event e) {
        Label title = new Label("#" + e.getIdEv() + " - " + safe(e.getTitleEv()));
        title.getStyleClass().add("card-title");

        int reserved = reservedByEventId.getOrDefault(e.getIdEv(), 0);
        int available = Math.max(0, e.getCapaciteEvnt() - reserved);
        String statusText = eventStatus(e);
        Label badge = new Label(statusText);
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(statusClassFor(e));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        Label line1 = new Label("Dates: " + fmtDateTime(e.getStartDateEv()) + " -> " + fmtDateTime(e.getEndDateEv()));
        line1.getStyleClass().add("card-meta");
        Label line2 = new Label("Lieu: " + safe(e.getLocalisationEv()) + "   |   Organisateur: " + safe(e.getOrganisateurName()));
        line2.getStyleClass().add("card-meta");
        Label line3 = new Label("Capacite: " + e.getCapaciteEvnt() + "   |   Reserve: " + reserved + "   |   Disponible: " + available);
        line3.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, line1, line2, line3);
        card.getStyleClass().add("resource-card");
        return card;
    }

    private VBox buildBookingCard(EventBooking b) {
        Label title = new Label("Evenement: " + safe(b.getEventTitle()));
        title.getStyleClass().add("card-title");

        Label badge = new Label("Places: " + b.getNumTicketBk());
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add("status-pending");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        Label line1 = new Label("Dates: " + fmtDateTime(b.getEventStart()) + " -> " + fmtDateTime(b.getEventEnd()));
        line1.getStyleClass().add("card-meta");
        Label line2 = new Label("Reservation le: " + fmtDateTime(b.getBookingDate()));
        line2.getStyleClass().add("card-meta");

        Label line3 = new Label("Client: " + safe(b.getClientName()));
        line3.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, line1, line2, line3);
        card.getStyleClass().add("resource-card");
        return card;
    }

    private String eventStatus(Event e) {
        LocalDateTime now = LocalDateTime.now();
        if (e.getEndDateEv() != null && e.getEndDateEv().isBefore(now)) {
            return "Termine";
        }
        if (e.getStartDateEv() != null && e.getStartDateEv().isBefore(now)) {
            return "En cours";
        }
        return "A venir";
    }

    private String statusClassFor(Event e) {
        LocalDateTime now = LocalDateTime.now();
        if (e.getEndDateEv() != null && e.getEndDateEv().isBefore(now)) {
            return "status-refused";
        }
        if (e.getStartDateEv() != null && e.getStartDateEv().isBefore(now)) {
            return "status-pending";
        }
        return "status-accepted";
    }

    private Event requireEventSelection() {
        Event selected = listEvents.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez un evenement.");
        }
        return selected;
    }

    private EventBooking requireBookingSelection() {
        EventBooking selected = listBookings.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez une reservation.");
        }
        return selected;
    }

    private void authorizeManage() {
        UserRole role = SessionContext.getCurrentRole();
        if (role != UserRole.ADMIN && role != UserRole.GERANT) {
            throw new IllegalStateException("Acces refuse: ADMIN ou GERANT requis.");
        }
    }

    private void authorizeClient() {
        if (SessionContext.getCurrentRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Acces reserve aux clients.");
        }
    }

    private int parsePositiveInt(String value, String msg) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(msg);
        }
        try {
            int v = Integer.parseInt(value.trim());
            if (v <= 0) {
                throw new IllegalArgumentException(msg);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(msg);
        }
    }

    private String fmtDateTime(LocalDateTime dt) {
        return dt == null ? "-" : dt.format(dateTimeFmt);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private boolean containsIgnoreCase(String value, String q) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase().contains(q);
    }

    private void openModal(Parent root, String title) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private void showError(String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }
}
