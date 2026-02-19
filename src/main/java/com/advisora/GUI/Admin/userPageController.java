package com.advisora.GUI.Admin;

import com.advisora.Model.User;
import com.advisora.Services.UserService;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import java.io.File;

import java.util.List;
import java.util.stream.Collectors;

public class userPageController {

    // --- FXML (must exist in usersPage.fxml) ---
    @FXML private TextField searchField;
    @FXML private ListView<User> usersList;
    @FXML private Label rightStatus;

    @FXML private Label lblTotalUsers;
    @FXML private Label lblAdmins;
    @FXML private Label lblGerants;

    @FXML private StackPane overlay;
    @FXML private VBox modalBox;

    // --- data ---
    private final UserService userService = new UserService();
    private final ObservableList<User> usersObs = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        usersList.setItems(usersObs);

        // Card-like ListView
        usersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);

                if (empty || u == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label title = new Label("#" + u.getId() + " - " + safe(u.getPrenom()) + " " + safe(u.getNom()));
                title.getStyleClass().add("card-title");

                Label meta = new Label(
                        safe(u.getEmail()) + "   |   CIN: " + safe(u.getCin()) + "   |   Tel: " + safe(u.getNumTel())
                );
                meta.getStyleClass().add("card-meta");

                Label roleBadge = new Label(u.getRole() == null ? "" : u.getRole().name());
                roleBadge.getStyleClass().addAll("status-badge", "role-badge");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button editBtn = new Button("Edit");
                editBtn.getStyleClass().add("icon-btn");
                editBtn.setOnAction(e -> openEditDialog(u));

                Button delBtn = new Button("Delete");
                delBtn.getStyleClass().addAll("icon-btn", "danger");
                delBtn.setOnAction(e -> {
                    // prevent list click/double-click effects
                    e.consume();
                    usersList.getSelectionModel().select(u);
                    deleteSelected();
                });

                HBox topRow = new HBox(10, title, spacer, roleBadge, editBtn, delBtn);
                topRow.setAlignment(Pos.CENTER_LEFT);

                ImageView avatar = new ImageView();
                avatar.setFitWidth(40);
                avatar.setFitHeight(40);
                avatar.setPreserveRatio(true);

                String path = u.getImagePath();
                if (path == null || path.trim().isEmpty()) {
                    avatar.setImage(new Image(getClass().getResourceAsStream("/GUI/Admin/icons/profile.png")));
                } else {
                    avatar.setImage(new Image(new File(path).toURI().toString()));
                }

                HBox header = new HBox(10, avatar, topRow);
                header.setAlignment(Pos.CENTER_LEFT);

                VBox card = new VBox(8, header, meta);

                card.getStyleClass().add("user-card");

                setText(null);
                setGraphic(card);
            }
        });

        // ✁EDouble-click => open edit dialog (single click only selects)
        usersList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                User u = usersList.getSelectionModel().getSelectedItem();
                if (u != null) openEditDialog(u);
            }
        });

        // Search
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldV, q) -> applyFilter(q));
        }

        refreshList();
        if (rightStatus != null) rightStatus.setText("Ready.");
    }

    // =========================
    // TOP BUTTONS
    // =========================
    @FXML
    private void handleRefresh() {
        refreshList();
        if (rightStatus != null) rightStatus.setText("Refreshed.");
    }

    // =========================
    // SORT MENU (FXML calls)
    // =========================
    @FXML
    private void onSortRoleAsc() {
        usersObs.sort((a, b) -> safeRole(a).compareToIgnoreCase(safeRole(b)));
        if (rightStatus != null) rightStatus.setText("Sorted by role (A ↁEZ)");
    }

    @FXML
    private void onSortRoleDesc() {
        usersObs.sort((a, b) -> safeRole(b).compareToIgnoreCase(safeRole(a)));
        if (rightStatus != null) rightStatus.setText("Sorted by role (Z ↁEA)");
    }

    @FXML
    private void onSortNomAsc() {
        usersObs.sort((a, b) -> safe(a.getNom()).compareToIgnoreCase(safe(b.getNom())));
        if (rightStatus != null) rightStatus.setText("Sorted by name (A ↁEZ)");
    }

    @FXML
    private void onSortNomDesc() {
        usersObs.sort((a, b) -> safe(b.getNom()).compareToIgnoreCase(safe(a.getNom())));
        if (rightStatus != null) rightStatus.setText("Sorted by name (Z ↁEA)");
    }

    // =========================
    // DIALOGS (ADD / EDIT)
    // =========================
    @FXML
    private void openAddDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Admin/UserDialog.fxml"));
            Node dialog = loader.load();

            UserDialogController ctrl = loader.getController();
            ctrl.initAdd(
                    msg -> { if (rightStatus != null) rightStatus.setText(msg); refreshList(); },
                    this::closeDialog
            );

            showDialog(dialog);
        } catch (Exception e) {
            if (rightStatus != null) rightStatus.setText("Open dialog failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void deleteSelected() {
        User u = usersList.getSelectionModel().getSelectedItem();
        if (u == null) {
            if (rightStatus != null) rightStatus.setText("Select a user first.");
            return;
        }

        try {
            userService.supprimerParId(u.getId());
            if (rightStatus != null) rightStatus.setText("User deleted ✁EID=" + u.getId());
            refreshList();
        } catch (Exception e) {
            if (rightStatus != null) rightStatus.setText("Delete failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void openEditDialog(User u) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Admin/UserDialog.fxml"));
            Node dialog = loader.load();

            UserDialogController ctrl = loader.getController();
            ctrl.initEdit(
                    u,
                    msg -> { if (rightStatus != null) rightStatus.setText(msg); refreshList(); },
                    this::closeDialog
            );

            showDialog(dialog);
        } catch (Exception e) {
            if (rightStatus != null) rightStatus.setText("Open edit failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showDialog(Node dialog) {
        modalBox.getChildren().setAll(dialog);
        overlay.setManaged(true);
        overlay.setVisible(true);
    }

    @FXML
    private void closeDialog() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        modalBox.getChildren().clear();
        usersList.getSelectionModel().clearSelection();
    }

    // =========================
    // DATA
    // =========================
    private void refreshList() {
        List<User> all = userService.afficher();
        usersObs.setAll(all);
        updateStats();
    }

    private void applyFilter(String q) {
        if (q == null || q.trim().isEmpty()) {
            refreshList();
            return;
        }

        String s = q.trim().toLowerCase();
        List<User> all = userService.afficher();

        usersObs.setAll(all.stream().filter(u ->
                safe(u.getNom()).toLowerCase().contains(s) ||
                        safe(u.getPrenom()).toLowerCase().contains(s) ||
                        safe(u.getEmail()).toLowerCase().contains(s) ||
                        safeRole(u).toLowerCase().contains(s) ||
                        safe(u.getCin()).toLowerCase().contains(s) ||
                        safe(u.getNumTel()).toLowerCase().contains(s)
        ).collect(Collectors.toList()));

        updateStats(); // stats reflect filtered list (remove if you want global stats)
    }

    private void updateStats() {
        int total = usersObs.size();
        long admins = usersObs.stream().filter(u -> u.getRole() == UserRole.ADMIN).count();
        long gerants = usersObs.stream().filter(u -> u.getRole() == UserRole.GERANT).count();

        if (lblTotalUsers != null) lblTotalUsers.setText(String.valueOf(total));
        if (lblAdmins != null) lblAdmins.setText(String.valueOf(admins));
        if (lblGerants != null) lblGerants.setText(String.valueOf(gerants));
    }

    private String safeRole(User u) {
        return (u == null || u.getRole() == null) ? "" : u.getRole().name();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
