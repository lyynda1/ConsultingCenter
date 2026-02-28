package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Model.ressource.Ressource;
import com.advisora.Services.ressource.CatalogueFournisseurService;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class ResourceListController {
    @FXML private ComboBox<CatalogueFournisseur> comboSupplier;
    @FXML private ListView<Ressource> listResources;
    @FXML private Label lblStatus;
    @FXML private Label lblTotalResources;
    @FXML private Label lblTotalStock;
    @FXML private Label lblAvailableResources;

    private final CatalogueFournisseurService fournisseurService = new CatalogueFournisseurService();
    private final RessourceService service = new RessourceService();
    private final ObservableList<Ressource> data = FXCollections.observableArrayList();

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
            comboSupplier.valueProperty().addListener((obs, oldV, v) -> refresh());
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
    private void onHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/ReservationList.fxml"));
            Parent root = loader.load();
            openModal(root, "Historique reservations");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void refresh() {
        CatalogueFournisseur supplier = comboSupplier.getValue();
        List<Ressource> list = (supplier == null)
                ? service.afficher()
                : service.getByFournisseur(supplier.getIdFr());
        data.setAll(list);
        updateStats(list);
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
        Label meta2 = new Label("Fournisseur ID: " + r.getIdFr());
        meta2.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, meta1, meta2);
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
