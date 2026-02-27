package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.Services.ressource.CatalogueFournisseurService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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

public class SupplierListController {
    @FXML private ListView<CatalogueFournisseur> listSuppliers;
    @FXML private Label lblStatus;
    @FXML private Label lblTotalSuppliers;
    @FXML private Label lblTotalProducts;
    @FXML private Label lblActiveSuppliers;

    private final CatalogueFournisseurService service = new CatalogueFournisseurService();
    private final ObservableList<CatalogueFournisseur> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        try {
            listSuppliers.setItems(data);
            listSuppliers.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(CatalogueFournisseur item, boolean empty) {
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
        } catch (Exception ex) {
            lblStatus.setText("Erreur chargement fournisseurs: " + ex.getMessage());
        }
    }

    @FXML
    private void onAdd() {
        try {
            authorizeManage();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/SupplierForm.fxml"));
            Parent root = loader.load();
            SupplierFormController controller = loader.getController();
            controller.initForCreate();
            openModal(root, "Ajouter fournisseur");
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onEdit() {
        try {
            authorizeManage();
            CatalogueFournisseur selected = requireSelection();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/SupplierForm.fxml"));
            Parent root = loader.load();
            SupplierFormController controller = loader.getController();
            controller.initForEdit(selected);
            openModal(root, "Modifier fournisseur");
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        try {
            authorizeManage();
            CatalogueFournisseur selected = requireSelection();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer ce fournisseur ?", ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            service.supprimer(selected);
            lblStatus.setText("Fournisseur supprime #" + selected.getIdFr());
            refresh();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        List<CatalogueFournisseur> fournisseurs = service.afficher();
        data.setAll(fournisseurs);
        updateStats(fournisseurs);
    }

    private void updateStats(List<CatalogueFournisseur> fournisseurs) {
        int total = fournisseurs == null ? 0 : fournisseurs.size();
        int totalProducts = fournisseurs == null ? 0 : fournisseurs.stream().mapToInt(CatalogueFournisseur::getQuantite).sum();
        long active = fournisseurs == null ? 0 : fournisseurs.stream().filter(f -> f.getQuantite() > 0).count();

        if (lblTotalSuppliers != null) lblTotalSuppliers.setText(String.valueOf(total));
        if (lblTotalProducts != null) lblTotalProducts.setText(String.valueOf(totalProducts));
        if (lblActiveSuppliers != null) lblActiveSuppliers.setText(String.valueOf(active));
    }

    private VBox buildCard(CatalogueFournisseur f) {
        Label title = new Label("#" + f.getIdFr() + " - " + safe(f.getNomFr()));
        title.getStyleClass().add("card-title");

        Label badge = new Label(f.getQuantite() > 0 ? "ACTIF" : "VIDE");
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(f.getQuantite() > 0 ? "status-accepted" : "status-refused");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        Label company = new Label("Societe: " + safe(f.getFournisseur()));
        company.getStyleClass().add("card-meta");
        Label contact = new Label("Email: " + safe(f.getEmailFr()) + "   |   Tel: " + safe(f.getNumTelFr()));
        contact.getStyleClass().add("card-meta");
        Label location = new Label("Localisation: " + safe(f.getLocalisationFr()) + "   |   Produits: " + f.getQuantite());
        location.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, company, contact, location);
        card.getStyleClass().add("resource-card");
        return card;
    }

    private CatalogueFournisseur requireSelection() {
        CatalogueFournisseur selected = listSuppliers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez un fournisseur.");
        }
        return selected;
    }

    private void authorizeManage() {
        UserRole r = SessionContext.getCurrentRole();
        if (r != UserRole.ADMIN && r != UserRole.GERANT) {
            throw new IllegalStateException("Acces refuse: ADMIN ou GERANT requis.");
        }
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
        a.setHeaderText("Gestion fournisseurs");
        a.showAndWait();
    }

    private String safe(String value) {
        return value == null ? "-" : value.trim();
    }
}
