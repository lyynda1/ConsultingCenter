package com.advisora.GUI;

import com.advisora.accessibility.VoiceAssistantManager;
import com.advisora.GUI.Game.GameHubController;
import com.advisora.GUI.Investissement.InvestmentListController;
import com.advisora.GUI.Transaction.TransactionListController;
import com.advisora.Model.strategie.Notification;
import com.advisora.Model.user.User;
import com.advisora.Services.projet.TaskService;
import com.advisora.Services.strategie.NotificationManager;
import com.advisora.Services.user.AuthSessionService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import com.advisora.utils.AvatarUtil;
import com.advisora.utils.LocalSessionStore;
import com.advisora.utils.SceneThemeApplier;
import com.advisora.utils.ThemeMode;
import com.advisora.utils.ThemeManager;
import com.advisora.utils.i18n.I18n;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InterfaceGeneralController {

    private final AuthSessionService authSessionService = new AuthSessionService();
    private final TaskService taskService = new TaskService();

    @FXML private StackPane contentHost;

    @FXML private Button homeBtn;
    @FXML private Button usersBtn;
    @FXML private Button ressourceBtn;
    @FXML private Button projectsBtn;
    @FXML private Button eventsBtn;
    @FXML private Button strategiesBtn;
    @FXML private Button investissementsBtn;
    @FXML private Button parametreBtn;
    @FXML private Label lblWelcomeTitle;
    @FXML private Label lblWelcomeSub;

    @FXML private Button voiceToggleBtn;
    @FXML private Button themeToggleBtn;

    @FXML private StackPane overlay;
    @FXML private VBox modalBox;

    @FXML private ImageView profileImageView;
    @FXML private Label nomUser;
    @FXML private Label roleStatut;

    @FXML private Button notificationButton;
    @FXML private Pane popupLayer;

    @FXML private BorderPane root;
    @FXML private HBox topBar;

    @FXML private Circle notificationBadge;
    @FXML private Label notifCount;

    private HBox notificationPanel;
    private final List<Button> navButtons = new ArrayList<>();

    // =========================
    // ✅ FXML loader helpers (ALWAYS with bundle)
    // =========================
    private Parent loadView(String fxmlPath) throws Exception {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) throw new IllegalArgumentException("FXML introuvable: " + fxmlPath);
        FXMLLoader loader = new FXMLLoader(url, I18n.bundle());
        return loader.load();
    }

    private <T> Loaded<T> loadViewWithController(String fxmlPath) throws Exception {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) throw new IllegalArgumentException("FXML introuvable: " + fxmlPath);
        FXMLLoader loader = new FXMLLoader(url, I18n.bundle());
        Parent root = loader.load();
        return new Loaded<>(root, loader.getController());
    }

    private static final class Loaded<T> {
        final Parent root;
        final T controller;
        Loaded(Parent root, T controller) {
            this.root = root;
            this.controller = controller;
        }
    }

    // =========================
    // init
    // =========================
    @FXML
    public void initialize() throws SQLException {
        User u = SessionContext.getCurrentUser();
        initNavButtons();

        NotificationManager.getInstance().loadNotificationsForUser(u.getRole(), u.getId());
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
        if (usersBtn != null) {
            usersBtn.setVisible(isAdmin);
            usersBtn.setManaged(isAdmin);
        }

        handleOpenHome();
        AvatarUtil.makeCircular(profileImageView);

        refreshThemeToggleText();
        hookVoiceToggleSync();

        // ✅ react to language changes
        com.advisora.utils.i18n.I18n.localeProperty().addListener((obs, oldL, newL) -> refreshI18nTexts());
        refreshI18nTexts();
    }
    private void refreshI18nTexts() {
        if (homeBtn != null) homeBtn.setText(I18n.tr("nav.home"));
        if (usersBtn != null) usersBtn.setText(I18n.tr("nav.users"));
        if (ressourceBtn != null) ressourceBtn.setText(I18n.tr("nav.resources"));
        if (projectsBtn != null) projectsBtn.setText(I18n.tr("nav.projects"));
        if (eventsBtn != null) eventsBtn.setText(I18n.tr("nav.events"));
        if (strategiesBtn != null) strategiesBtn.setText(I18n.tr("nav.strategies"));
        if (investissementsBtn != null) investissementsBtn.setText(I18n.tr("nav.investments"));
        if (parametreBtn != null) parametreBtn.setText(I18n.tr("nav.settings"));

        if (themeToggleBtn != null) {
            themeToggleBtn.setText(
                    com.advisora.utils.ThemeManager.getCurrentMode() == com.advisora.utils.ThemeMode.DARK
                            ? I18n.tr("top.themeLight")
                            : I18n.tr("top.themeNight")
            );
        }
        if (voiceToggleBtn != null) {
            voiceToggleBtn.setText(
                    com.advisora.accessibility.VoiceAssistantManager.isEnabled()
                            ? I18n.tr("top.voiceOn")
                            : I18n.tr("top.voiceOff")
            );
        }
    }

    private void initNavButtons() {
        navButtons.clear();
        if (homeBtn != null) navButtons.add(homeBtn);
        if (usersBtn != null) navButtons.add(usersBtn);
        if (ressourceBtn != null) navButtons.add(ressourceBtn);
        if (projectsBtn != null) navButtons.add(projectsBtn);
        if (eventsBtn != null) navButtons.add(eventsBtn);
        if (strategiesBtn != null) navButtons.add(strategiesBtn);
        if (investissementsBtn != null) navButtons.add(investissementsBtn);
        if (parametreBtn != null) navButtons.add(parametreBtn);
    }

    private void setActiveNav(Button active) {
        for (Button btn : navButtons) {
            if (btn == null) continue;
            btn.getStyleClass().remove("nav-active");
        }
        if (active != null && !active.getStyleClass().contains("nav-active")) {
            active.getStyleClass().add("nav-active");
        }
    }

    // =========================
    // Overlay
    // =========================
    @FXML
    private void closeOverlay() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        modalBox.getChildren().clear();
    }

    @FXML
    private void openProfile() {
        try {
            Loaded<com.advisora.GUI.Profil.ProfileController> loaded =
                    loadViewWithController("/GUI/Profile/ProfilePage.fxml");

            Node view = loaded.root;
            com.advisora.GUI.Profil.ProfileController ctrl = loaded.controller;
            ctrl.setOnClose(this::closeOverlay);

            modalBox.getChildren().setAll(view);
            overlay.setManaged(true);
            overlay.setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Profile", buildErrorMessage(e));
        }
    }

    // =========================
    // Pages
    // =========================
    @FXML
    private void handleOpenHome() {
        try {
            Loaded<HomeController> loaded = loadViewWithController("/views/Home.fxml");
            loaded.controller.setOnOpenGames(this::handleOpenGames);

            contentHost.getChildren().setAll(loaded.root);
            setActiveNav(homeBtn);
        } catch (Exception ex) {
            showError("Home", buildErrorMessage(ex));
        }
    }

    private void handleOpenGames() {
        try {
            Loaded<GameHubController> loaded = loadViewWithController("/views/game/GameHub.fxml");
            loaded.controller.setOnBack(this::handleOpenHome);

            contentHost.getChildren().setAll(loaded.root);
        } catch (Exception ex) {
            showError("Games", buildErrorMessage(ex));
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
            setActiveNav(usersBtn);
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Gestion Utilisateurs", buildErrorMessage(ex));
        }
    }

    @FXML
    private void handleOpenProjects() {
        try {
            if (SessionContext.getCurrentRole() == UserRole.CLIENT) {
                taskService.checkAndNotifyNearFinishForClientProjects(SessionContext.getCurrentUserId());
            } else {
                taskService.checkAndNotifyNearFinishAllProjects();
            }
            loadProjectListIntoContent();
            setActiveNav(projectsBtn);
        } catch (Exception ex) {
            showError("Gestion Projets", buildErrorMessage(ex));
        }
    }

    @FXML
    private void handleOpenEvents() {
        try {
            loadIntoContent("/views/event/EventList.fxml");
            setActiveNav(eventsBtn);
        } catch (Exception ex) {
            showError("Gestion Evenements", buildErrorMessage(ex));
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
                showError("Gestion Ressources", "Accès refusé.");
                return;
            }
            setActiveNav(ressourceBtn);
        } catch (Exception ex) {
            showError("Gestion Ressources", buildErrorMessage(ex));
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

            // ✅ LOAD THE STRATEGY PAGE (not InterfaceGeneral again)
            loadIntoContent("/views/strategie/interfaceStrategie.fxml");
            setActiveNav(strategiesBtn);

        } catch (Exception ex) {
            showError("Gestion Strategies", buildErrorMessage(ex));
        }
    }

    @FXML
    private void handleOpenInvestissements() {
        try {
            Loaded<InvestmentListController> loaded =
                    loadViewWithController("/views/investissement/investissementContent.fxml");

            loaded.controller.setOnOpenTransactions(this::handleOpenTransactions);

            contentHost.getChildren().setAll(loaded.root);
            setActiveNav(investissementsBtn);
        } catch (Exception ex) {
            showError("Gestion Investissements", buildErrorMessage(ex));
        }
    }

    @FXML
    private void handleOpenTransactions() {
        try {
            Loaded<TransactionListController> loaded =
                    loadViewWithController("/views/transaction/transactionContent.fxml");

            loaded.controller.setOnOpenInvestissements(this::handleOpenInvestissements);

            contentHost.getChildren().setAll(loaded.root);
            setActiveNav(investissementsBtn);
        } catch (Exception ex) {
            showError("Gestion Transactions", buildErrorMessage(ex));
        }
    }

    @FXML
    private void handleRefresh() {
        handleOpenHome();
    }

    @FXML
    private void handleLogout() {
        try {
            String token = LocalSessionStore.load();
            authSessionService.revokeSession(token);
            LocalSessionStore.clear();

            NotificationManager.getInstance().getNotifications().clear();
            SessionContext.clear();

            // ✅ bundle-enabled login load
            Parent loginRoot = loadView("/GUI/Auth/login.fxml");

            Stage stage = (Stage) contentHost.getScene().getWindow();
            SceneThemeApplier.setScene(stage, loginRoot);
            stage.setTitle("Advisora - Login");

        } catch (Exception ex) {
            showError("Logout", buildErrorMessage(ex));
        }
    }

    // =========================
    // ✅ Content loaders (bundle)
    // =========================
    private void loadIntoContent(String fxmlPath) throws Exception {
        Parent root = loadView(fxmlPath);
        contentHost.getChildren().setAll(root);
    }

    private void loadProjectListIntoContent() throws Exception {
        Loaded<com.advisora.GUI.Project.ProjectListController> loaded =
                loadViewWithController("/views/project/ProjectList.fxml");

        loaded.controller.setContentNavigator(view -> contentHost.getChildren().setAll(view));
        contentHost.getChildren().setAll(loaded.root);
    }

    // =========================
    // Notifications UI
    // =========================
    @FXML
    private void handleNotificationClick() {
        try {
            if (notificationPanel != null && popupLayer.getChildren().contains(notificationPanel)) {
                popupLayer.getChildren().remove(notificationPanel);
                notificationPanel = null;
                return;
            }

            Parent panel = loadView("/views/Notification.fxml");
            notificationPanel = (HBox) panel;
            popupLayer.getChildren().add(notificationPanel);

            notificationPanel.applyCss();
            notificationPanel.layout();

            Bounds b = notificationButton.localToScene(notificationButton.getBoundsInLocal());
            Point2D p = popupLayer.sceneToLocal(b.getMinX(), b.getMaxY());

            double panelW = notificationPanel.prefWidth(-1);
            double panelH = notificationPanel.prefHeight(-1);

            double x = p.getX() + b.getWidth() - panelW;
            double y = p.getY() + 6;

            double maxX = popupLayer.getWidth() - panelW - 10;
            double maxY = popupLayer.getHeight() - panelH - 10;
            x = Math.max(10, Math.min(x, maxX));
            y = Math.max(10, Math.min(y, maxY));

            notificationPanel.relocate(x, y);

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
            showError("Notification", buildErrorMessage(e));
        }
    }

    // =========================
    // Voice / Theme
    // =========================
    @FXML
    private void handleToggleVoiceAssistant() {
        Scene scene = contentHost != null ? contentHost.getScene() : null;
        boolean enabled = VoiceAssistantManager.toggle(scene);
        System.out.println("[UI] Voice assistant " + (enabled ? "enabled" : "disabled"));
        refreshVoiceToggleText();
    }

    @FXML
    private void handleToggleTheme() {
        Scene scene = contentHost != null ? contentHost.getScene() : null;
        ThemeMode mode = ThemeManager.toggleMode(scene);
        if (mode == ThemeMode.DARK) {
            System.out.println("[UI] Dark mode enabled");
        } else {
            System.out.println("[UI] Light mode enabled");
        }
        refreshThemeToggleText();
    }

    private void refreshThemeToggleText() {
        if (themeToggleBtn == null) return;
        // ✅ keys you should add:
        // top.themeLight / top.themeNight
        themeToggleBtn.setText(
                ThemeManager.getCurrentMode() == ThemeMode.DARK
                        ? I18n.tr("top.themeLight")
                        : I18n.tr("top.themeNight")
        );
    }

    private void refreshVoiceToggleText() {
        if (voiceToggleBtn == null) return;
        // ✅ keys you should add:
        // top.voiceOn / top.voiceOff
        voiceToggleBtn.setText(
                VoiceAssistantManager.isEnabled()
                        ? I18n.tr("top.voiceOn")
                        : I18n.tr("top.voiceOff")
        );
    }

    private void hookVoiceToggleSync() {
        if (contentHost == null) {
            refreshVoiceToggleText();
            return;
        }
        contentHost.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                VoiceAssistantManager.registerStateListener(newScene, enabled -> refreshVoiceToggleText());
            }
            refreshVoiceToggleText();
        });
        Scene current = contentHost.getScene();
        if (current != null) {
            VoiceAssistantManager.registerStateListener(current, enabled -> refreshVoiceToggleText());
        }
        refreshVoiceToggleText();
    }

    // =========================
    // Notification badge
    // =========================
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

    // =========================
    // Settings
    // =========================
    @FXML
    public void handleOpenSettings(ActionEvent actionEvent) {
        try {
            loadIntoContent("/views/parametres.fxml");
            if (parametreBtn != null) setActiveNav(parametreBtn);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Paramètres", buildErrorMessage(e));
        }
    }

    // =========================
    // Errors
    // =========================
    private void showError(String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private String buildErrorMessage(Throwable ex) {
        if (ex == null) return "Erreur inconnue.";
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
}