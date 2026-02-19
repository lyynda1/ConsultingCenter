package com.advisora.GUI;

import com.advisora.GUI.Investissement.InvestmentListController;
import com.advisora.GUI.Transaction.TransactionListController;
import com.advisora.Model.Notification;
import com.advisora.Model.User;
import com.advisora.Services.NotificationManager;
import com.advisora.Services.SessionContext;
import com.advisora.enums.UserRole;
import com.advisora.utils.AvatarUtil;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class InterfaceGeneralController {

    @FXML private StackPane contentHost;
    @FXML private Button usersBtn;
    @FXML private Button eventsBtn;
    @FXML private StackPane overlay;
    @FXML private VBox modalBox;

    @FXML private ImageView profileImageView;

    @FXML private Label nomUser;
    @FXML private Label roleStatut;
    @FXML
    private Button notificationButton;
    @FXML private Pane popupLayer;

    private HBox notificationPanel;


    @FXML
    private HBox topBar; // Add this field and link it to your top bar HBox in FXML

    @FXML
    private Circle notificationBadge;

    @FXML
    private Label notifCount;

    @FXML
    public void initialize() {

        User u = SessionContext.getCurrentUser();
        if (u.getRole() != UserRole.ADMIN && u.getRole() != UserRole.GERANT) {
            notificationButton.setVisible(false);
            notificationButton.setManaged(false);
            notificationBadge.setVisible(false);
            notifCount.setVisible(false);
        }
        NotificationManager.getInstance().loadNotifications(); // ✅ important to refresh data from DB

        updateNotificationBadge();


        NotificationManager.getInstance().getNotifications().addListener(
                (ListChangeListener<Notification>) c -> updateNotificationBadge()
        );
        nomUser.setText(u.getNom());
        roleStatut.setText(u.getRole().name());

        String path = u.getImagePath();
        if (path == null || path.trim().isEmpty()) {
            profileImageView.setImage(new Image(getClass().getResourceAsStream("/GUI/Admin/icons/profile.png")));
        } else {
            profileImageView.setImage(new Image(new java.io.File(path).toURI().toString()));
        }

        boolean isAdmin = (u.getRole() == UserRole.ADMIN);
        usersBtn.setVisible(isAdmin);
        usersBtn.setManaged(isAdmin);

        if (eventsBtn != null) {
            if (u.getRole() == UserRole.CLIENT) {
                eventsBtn.setText("Evenements");
            } else {
                eventsBtn.setText("Gestion Evenements");
            }
        }

        handleOpenHome();
        AvatarUtil.makeCircular(profileImageView);
    }
    @FXML
    private void closeOverlay() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        modalBox.getChildren().clear();
    }

    @FXML
    private void openProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Profile/ProfilePage.fxml"));
            Node view = loader.load();

            com.advisora.GUI.Profil.ProfileController ctrl = loader.getController();
            ctrl.setOnClose(this::closeOverlay);

            modalBox.getChildren().setAll(view);
            overlay.setManaged(true);
            overlay.setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    private void handleOpenHome() {
        try {
            loadIntoContent("/views/Home.fxml");
        } catch (Exception ex) {
            showError("Home", ex.getMessage());
        }
    }

    @FXML
    private void handleOpenUsers() {
        UserRole r = SessionContext.getCurrentRole();
        if (r != UserRole.ADMIN) {
            showError("Gestion Utilisateurs", "Accès refusé: ADMIN requis.");
            return;
        }
        try {
            loadIntoContent("/GUI/Admin/UsersPage.fxml");
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Gestion Utilisateurs", ex.getMessage());
        }
    }

    @FXML
    private void handleOpenProjects() {
        try {
            loadIntoContent("/views/project/ProjectList.fxml");
        } catch (Exception ex) {
            showError("Gestion Projets", ex.getMessage());
        }
    }

    @FXML
    private void handleOpenEvents() {
        try {
            loadIntoContent("/views/event/EventList.fxml");
        } catch (Exception ex) {
            showError("Gestion Evenements", ex);
        }
    }

    @FXML
    private void handleOpenRessource() {
        try {
            UserRole role = SessionContext.getCurrentRole();
            if (role == UserRole.CLIENT) {
                loadIntoContent("/views/resource/CatalogView.fxml");
            } else if (role == UserRole.ADMIN || role == UserRole.GERANT) {
                loadIntoContent("/views/resource/InventoryDashboard.fxml");
            } else {
                showError("Gestion Ressources", "Acces refuse.");
            }
        } catch (Exception ex) {
            showError("Gestion Ressources", ex.getMessage());
        }
    }

    @FXML
    private void handleOpenStrategies() {
        try {
            UserRole r = SessionContext.getCurrentRole();
            if (r != UserRole.ADMIN && r != UserRole.GERANT) {
                showError("Gestion Strategies", "Accès refusé: GERANT ou ADMIN requis.");
                return;
            }
            loadIntoContent("/views/strategie/interfaceStrategie.fxml");
        } catch (Exception ex) {
            showError("Gestion Strategies", ex.getMessage());
        }
    }

    @FXML
    private void handleOpenInvestissements() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/investissement/investissementContent.fxml"));
            Parent root = loader.load();
            InvestmentListController ctrl = loader.getController();
            ctrl.setOnOpenTransactions(this::handleOpenTransactions);
            contentHost.getChildren().setAll(root);
        } catch (Exception ex) {
            showError("Gestion Investissements", ex.getMessage());
        }
    }

    @FXML
    private void handleOpenTransactions() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/transaction/transactionContent.fxml"));
            Parent root = loader.load();
            TransactionListController ctrl = loader.getController();
            ctrl.setOnOpenInvestissements(this::handleOpenInvestissements);
            contentHost.getChildren().setAll(root);
        } catch (Exception ex) {
            showError("Gestion Transactions", ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        handleOpenHome();
    }

    @FXML
    private void handleLogout() {
        try {
            SessionContext.clear();
            Parent root = FXMLLoader.load(getClass().getResource("/GUI/Auth/login.fxml"));
            Stage stage = (Stage) contentHost.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Advisora - Login");
        } catch (Exception ex) {
            showError("Logout", ex.getMessage());
        }
    }

    private void loadIntoContent(String fxmlPath) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        contentHost.getChildren().setAll(root);
    }

    private void showError(String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private void showError(String header, Throwable ex) {
        String message = buildErrorMessage(ex);
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private String buildErrorMessage(Throwable ex) {
        if (ex == null) {
            return "Erreur inconnue.";
        }
        StringBuilder sb = new StringBuilder();
        Throwable cur = ex;
        int depth = 0;
        while (cur != null && depth < 4) {
            if (depth == 0) {
                sb.append(cur.getClass().getSimpleName()).append(": ");
                sb.append(cur.getMessage() == null ? "(no message)" : cur.getMessage());
            } else {
                sb.append("\nCause ").append(depth).append(": ");
                sb.append(cur.getClass().getSimpleName()).append(": ");
                sb.append(cur.getMessage() == null ? "(no message)" : cur.getMessage());
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }
    @FXML
    private void handleNotificationClick() {
        try {
            // ✅ toggle close if already open
            if (notificationPanel != null && popupLayer.getChildren().contains(notificationPanel)) {
                popupLayer.getChildren().remove(notificationPanel);
                notificationPanel = null;
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/Notification.fxml"));
            notificationPanel = loader.load();

            popupLayer.getChildren().add(notificationPanel);

            // Let CSS/layout compute real size
            notificationPanel.applyCss();
            notificationPanel.layout();

            // Button bounds in scene coordinates
            Bounds b = notificationButton.localToScene(notificationButton.getBoundsInLocal());

            // Convert scene coords -> popupLayer coords
            Point2D p = popupLayer.sceneToLocal(b.getMinX(), b.getMaxY());

            double panelW = notificationPanel.prefWidth(-1);
            double panelH = notificationPanel.prefHeight(-1);

            double x = p.getX() + b.getWidth() - panelW;
            // aligned with button left
            double y = p.getY() + 6;  // below button

            // ✅ keep inside window
            double maxX = popupLayer.getWidth() - panelW - 10;
            double maxY = popupLayer.getHeight() - panelH - 10;
            x = Math.max(10, Math.min(x, maxX));
            y = Math.max(10, Math.min(y, maxY));

            notificationPanel.relocate(x, y);

            // ✅ close when clicking outside (without breaking other clicks)
            popupLayer.setOnMousePressed(e -> {
                if (notificationPanel != null &&
                        !notificationPanel.getBoundsInParent().contains(e.getX(), e.getY())) {
                    popupLayer.getChildren().remove(notificationPanel);
                    notificationPanel = null;
                    popupLayer.setOnMousePressed(null);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }









    public void updateNotificationCount(int count) {
        if (count > 0) {
            notifCount.setText(String.valueOf(count));
            notifCount.setVisible(true);
            notificationBadge.setVisible(true);
        } else {
            notifCount.setVisible(false);
            notificationBadge.setVisible(false);
        }
    }
    private void updateNotificationBadge() {
        int unread = NotificationManager.getInstance().getUnreadCount();
        boolean show = unread > 0;

        notificationBadge.setVisible(show);
        notifCount.setVisible(show);

        if (show) {
            notifCount.setText(unread > 99 ? "99+" : String.valueOf(unread));
        }
    }

}
