package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.Booking;
import com.advisora.Services.ressource.ReservationService;
import com.advisora.Services.ressource.ResourceReservationPdfExportService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReservationListController {
    private static final String ALL_FILTER = "Tous";

    @FXML private TextField txtSearchReservations;
    @FXML private ComboBox<String> comboProjectFilter;
    @FXML private ComboBox<String> comboResourceFilter;
    @FXML private ComboBox<String> comboSupplierFilter;
    @FXML private ComboBox<String> comboClientFilter;
    @FXML private ListView<Booking> listReservations;
    @FXML private Label lblReservationCount;
    @FXML private Label lblReservedQty;
    @FXML private Label lblStatus;

    private final ReservationService service = new ReservationService();
    private final ResourceReservationPdfExportService pdfExportService = new ResourceReservationPdfExportService();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(160));
    private final ObservableList<Booking> allData = FXCollections.observableArrayList();
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
        if (txtSearchReservations != null) {
            txtSearchReservations.textProperty().addListener((obs, oldV, newV) -> {
                searchDebounce.setOnFinished(e -> applySearchFilter());
                searchDebounce.playFromStart();
            });
        }
        bindFieldFilters();
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onResetFieldFilters() {
        if (txtSearchReservations != null) {
            txtSearchReservations.clear();
        }
        resetComboToAll(comboProjectFilter);
        resetComboToAll(comboResourceFilter);
        resetComboToAll(comboSupplierFilter);
        resetComboToAll(comboClientFilter);
        applySearchFilter();
    }

    @FXML
    private void onExportPdf() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter reservations (PDF)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("reservations_"
                    + SessionContext.getCurrentRole().name().toLowerCase(Locale.ROOT) + "_"
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

            String query = txtSearchReservations == null ? "" : txtSearchReservations.getText();
            pdfExportService.exportReservationReport(data, SessionContext.getCurrentRole(), query, out);
            ensureValidPdf(out);

            Alert ok = new Alert(Alert.AlertType.INFORMATION, "PDF genere:\n" + out.getAbsolutePath(), ButtonType.OK);
            ok.setHeaderText("Export reservations termine");
            ok.showAndWait();
            lblStatus.setText("Export PDF reservations termine.");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) lblStatus.getScene().getWindow();
        stage.close();
    }

    private void onEditReservation(Booking selected) {
        try {
            TextInputDialog dialog = new TextInputDialog(String.valueOf(selected.getQuantity()));
            dialog.setTitle("Modifier reservation");
            dialog.setHeaderText("Nouvelle quantite");
            dialog.setContentText("Quantite:");
            var result = dialog.showAndWait();
            if (result.isEmpty()) {
                return;
            }

            int qty = parseQuantity(result.get());
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

    private void onDeleteReservation(Booking selected) {
        try {
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
            allData.setAll(service.listClientReservations(SessionContext.getCurrentUserId()));
        } else {
            allData.setAll(service.listAllReservations());
        }
        populateFieldFilters();
        applySearchFilter();
    }

    private void applySearchFilter() {
        String query = txtSearchReservations == null ? "" : txtSearchReservations.getText();
        String selectedProject = selectedFilterValue(comboProjectFilter);
        String selectedResource = selectedFilterValue(comboResourceFilter);
        String selectedSupplier = selectedFilterValue(comboSupplierFilter);
        String selectedClient = selectedFilterValue(comboClientFilter);
        List<Booking> filtered = allData.stream()
                .filter(b -> matchesSearch(b, query))
                .filter(b -> matchesField(b.getProjectTitle(), selectedProject))
                .filter(b -> matchesField(b.getResourceName(), selectedResource))
                .filter(b -> matchesField(b.getFournisseurName(), selectedSupplier))
                .filter(b -> matchesField(b.getClientName(), selectedClient))
                .toList();

        data.setAll(filtered);
        updateStats();

        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            lblStatus.setText("Filtre dynamique actif: " + filtered.size() + " / " + allData.size());
        } else if (SessionContext.getCurrentRole() == UserRole.CLIENT) {
            lblStatus.setText("Reservations client chargees.");
        } else {
            lblStatus.setText("Reservations globales chargees.");
        }
    }

    private boolean matchesSearch(Booking booking, String query) {
        if (booking == null) {
            return false;
        }
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return true;
        }

        String hay = normalize(
                booking.getIdProj() + " "
                        + safe(booking.getProjectTitle()) + " "
                        + booking.getIdRs() + " "
                        + safe(booking.getResourceName()) + " "
                        + safe(booking.getFournisseurName()) + " "
                        + safe(booking.getClientName()) + " "
                        + booking.getQuantity()
        );

        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (!token.isBlank() && !hay.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesField(String value, String selectedFilter) {
        if (selectedFilter == null || selectedFilter.isBlank() || ALL_FILTER.equals(selectedFilter)) {
            return true;
        }
        return normalize(value).equals(normalize(selectedFilter));
    }

    private void bindFieldFilters() {
        bindCombo(comboProjectFilter);
        bindCombo(comboResourceFilter);
        bindCombo(comboSupplierFilter);
        bindCombo(comboClientFilter);
    }

    private void bindCombo(ComboBox<String> combo) {
        if (combo == null) {
            return;
        }
        combo.valueProperty().addListener((obs, oldV, newV) -> applySearchFilter());
    }

    private void populateFieldFilters() {
        populateCombo(comboProjectFilter, allData.stream().map(Booking::getProjectTitle).toList());
        populateCombo(comboResourceFilter, allData.stream().map(Booking::getResourceName).toList());
        populateCombo(comboSupplierFilter, allData.stream().map(Booking::getFournisseurName).toList());
        populateCombo(comboClientFilter, allData.stream().map(Booking::getClientName).toList());
    }

    private void populateCombo(ComboBox<String> combo, List<String> values) {
        if (combo == null) {
            return;
        }
        String previous = combo.getValue();
        ObservableList<String> items = FXCollections.observableArrayList();
        items.add(ALL_FILTER);
        values.stream()
                .map(this::safe)
                .filter(v -> !"-".equals(v))
                .distinct()
                .sorted(Comparator.comparing(v -> v.toLowerCase(Locale.ROOT)))
                .forEach(items::add);

        combo.setItems(items);
        if (previous != null && items.contains(previous)) {
            combo.setValue(previous);
        } else {
            combo.setValue(ALL_FILTER);
        }
    }

    private String selectedFilterValue(ComboBox<String> combo) {
        if (combo == null || combo.getValue() == null || combo.getValue().isBlank()) {
            return ALL_FILTER;
        }
        return combo.getValue();
    }

    private void resetComboToAll(ComboBox<String> combo) {
        if (combo == null || combo.getItems() == null || combo.getItems().isEmpty()) {
            return;
        }
        combo.setValue(ALL_FILTER);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
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

        Button btnEdit = buildEditButton(b);
        Button btnDelete = buildDeleteButton(b);

        HBox actionBox = new HBox(8, btnEdit, btnDelete);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, qtyBadge, actionBox);

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

    private Button buildEditButton(Booking booking) {
        SVGPath icon = new SVGPath();
        icon.setContent("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z M20.71 7.04c0.39-0.39 0.39-1.02 0-1.41l-2.34-2.34c-0.39-0.39-1.02-0.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z");
        icon.getStyleClass().add("icon-edit-shape");

        Button button = new Button();
        button.setGraphic(icon);
        button.setTooltip(new Tooltip("Modifier"));
        button.getStyleClass().addAll("btn-icon-action", "btn-icon-edit");
        button.setOnAction(e -> onEditReservation(booking));
        button.setFocusTraversable(false);
        return button;
    }

    private Button buildDeleteButton(Booking booking) {
        SVGPath icon = new SVGPath();
        icon.setContent("M6 19c0 1.1 0.9 2 2 2h8c1.1 0 2-0.9 2-2V7H6v12z M19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
        icon.getStyleClass().add("icon-delete-shape");

        Button button = new Button();
        button.setGraphic(icon);
        button.setTooltip(new Tooltip("Supprimer"));
        button.getStyleClass().addAll("btn-icon-action", "btn-icon-delete");
        button.setOnAction(e -> onDeleteReservation(booking));
        button.setFocusTraversable(false);
        return button;
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
