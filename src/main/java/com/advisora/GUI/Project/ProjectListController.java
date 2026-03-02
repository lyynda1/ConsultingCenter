package com.advisora.GUI.Project;

/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: GUI controller: user interactions and screen flow
*/

import com.advisora.utils.SceneThemeApplier;

import com.advisora.Model.projet.Project;
import com.advisora.Model.projet.ProjectAcceptanceEstimate;
import com.advisora.Model.projet.ProjectBadgeScore;
import com.advisora.Model.projet.ProjectDashboardData;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Model.user.User;
import com.advisora.Services.projet.NewsService;
import com.advisora.Services.projet.ProjectService;
import com.advisora.Services.projet.ProjectAcceptanceService;
import com.advisora.Services.projet.ProjectBadgeService;
import com.advisora.Services.projet.ProjectPdfExportService;
import com.advisora.Services.projet.ProjectStatsService;
import com.advisora.Services.strategie.ServiceObjective;
import com.advisora.Services.strategie.ServiceStrategie;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.user.UserService;
import com.advisora.enums.ProjectStatus;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.UserRole;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;



public class ProjectListController implements Initializable {

    @FXML private Button btnNewProject;
    @FXML private Button btnStats;
    @FXML private Button btnTop10;
    @FXML private Button btnEstimate;
    @FXML private Button btnExportPdf;
    @FXML private Button btnUsers;
    @FXML private ToggleButton tabAll;
    @FXML private ToggleButton tabValid;
    @FXML private ToggleButton tabPending;
    @FXML private ToggleButton tabRefused;
    @FXML private TextField txtSearch;
    @FXML private ListView<Project> projectList;
    @FXML private StackPane overlay;
    @FXML private VBox modalBox;
    private double dragOffsetX;
    private double dragOffsetY;
    private final ObservableList<Strategie> allObs = FXCollections.observableArrayList();
    private final ObservableList<Strategie> viewObs = FXCollections.observableArrayList();
    private static final String DB_SOURCE_LANG = "fr";


    private final ProjectService projectService = new ProjectService();
    private final ServiceStrategie strategyService = new ServiceStrategie();
    private final ServiceObjective serviceObjective = new ServiceObjective();
    private final ProjectAcceptanceService acceptanceService = new ProjectAcceptanceService();
    private final ProjectBadgeService badgeService = new ProjectBadgeService();
    private final ProjectStatsService statsService = new ProjectStatsService();
    private final ProjectPdfExportService pdfExportService = new ProjectPdfExportService();
    private final UserService userService = new UserService();
    private final NewsService newsService = new NewsService();

    private final ObservableList<Project> baseProjects = FXCollections.observableArrayList();
    private Consumer<Parent> contentNavigator;
    private Map<Integer, ProjectAcceptanceEstimate> pendingEstimates = new HashMap<>();
    private Map<Integer, ProjectBadgeScore> projectBadges = new HashMap<>();
    private Map<Integer, String> clientNamesById = new HashMap<>();
    private ProjectDashboardData dashboardData = new ProjectDashboardData();
    private List<Project> lastVisibleProjects = new ArrayList<>();
    // ---- new fields for the recommendation spinner ----
    private final ProgressIndicator recommendSpinner = new ProgressIndicator();
    private boolean recommendBusy = false;


    // Default sort: newest first (null-safe).
    private Comparator<Project> currentComparator =
            Comparator.comparing(Project::getCreatedAtProj, this::compareTsDescNullSafe);

    private String targetLang() {
        return com.advisora.utils.AppLanguage.ltTargetCode(); // "fr" or "en"
    }

    private String safe(String v) { return v == null ? "" : v.trim(); }

    private void translateDbAsync(String raw, java.util.function.Consumer<String> onUi) {
        String x = safe(raw);
        if (x.isBlank()) { onUi.accept(""); return; }
        com.advisora.AppServices.TRANSLATOR.translateAsync(x, DB_SOURCE_LANG, targetLang(), onUi);
    }

    /** Static UI text from i18n (changes instantly on locale change). */
    private void bindI18nText(Label lbl, String key, java.util.function.Supplier<String> suffix) {
        Runnable apply = () -> {
            String base = com.advisora.utils.i18n.I18n.tr(key);
            String extra = (suffix == null) ? "" : safe(suffix.get());
            lbl.setText(extra.isBlank() ? base : base + " " + extra);
        };
        apply.run();
        com.advisora.utils.i18n.LangBus.localeProperty()
                .addListener((obs, o, n) -> apply.run());
    }

    private void bindI18nText(Button btn, String key) {
        Runnable apply = () -> btn.setText(com.advisora.utils.i18n.I18n.tr(key));
        apply.run();
        com.advisora.utils.i18n.LangBus.localeProperty()
                .addListener((obs, o, n) -> apply.run());
    }


    /** Prefix is i18n, value is DB -> translate value when locale changes. */
    private void bindDbMeta(Label lbl, String prefixKey, String rawDbValue) {
        lbl.setUserData(rawDbValue);

        Runnable apply = () -> {
            String prefix = com.advisora.utils.i18n.I18n.tr(prefixKey);
            String raw = safe((String) lbl.getUserData());
            if (raw.isBlank()) {
                lbl.setText(prefix + " -");
                return;
            }
            // show raw instantly, then translated
            lbl.setText(prefix + " " + raw);
            translateDbAsync(raw, tr -> lbl.setText(prefix + " " + tr));
        };

        apply.run();
        com.advisora.utils.i18n.LangBus.localeProperty()
                .addListener((obs, o, n) -> apply.run());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupRoleUI();
        setupCardRenderer();
        // new: spinner starts invisible
        recommendSpinner.setMaxSize(80, 80);
        recommendSpinner.setVisible(false);

        // Keep ListView non-null and stable
        projectList.setItems(FXCollections.observableArrayList());

        loadProjectsFromService();
    }

    public void setContentNavigator(Consumer<Parent> contentNavigator) {
        this.contentNavigator = contentNavigator;
    }

    // =========================
    // UI bootstrap
    // =========================
    private void setupRoleUI() {
        boolean canCreateProject = SessionContext.isClient() || SessionContext.getCurrentRole() == UserRole.ADMIN;
        btnNewProject.setDisable(!canCreateProject);
        if (btnEstimate != null) {
            btnEstimate.setDisable(!canCreateProject);
        }
        if (btnExportPdf != null) {
            btnExportPdf.setDisable(false);
        }
        if (btnStats != null) {
            btnStats.setDisable(false);
        }

        boolean isAdmin = SessionContext.getCurrentRole() == UserRole.ADMIN;
        btnUsers.setVisible(isAdmin);
        btnUsers.setManaged(isAdmin);

        if (!(SessionContext.isManager() || isAdmin)) {
            tabAll.setVisible(false);     tabAll.setManaged(false);
            tabValid.setVisible(false);   tabValid.setManaged(false);
            tabPending.setVisible(false); tabPending.setManaged(false);
            tabRefused.setVisible(false); tabRefused.setManaged(false);
        }
    }




    private void setupCardRenderer() {
        projectList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                try {
                    setText(null);
                    setGraphic(buildCard(item));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    setGraphic(null);
                    setText("ERROR: " + ex.getMessage());
                }
            }
        });
    }
    private void bindDbText(Label lbl, String rawDbText) {
        lbl.setUserData(rawDbText);

        Runnable apply = () -> {
            String raw = safe((String) lbl.getUserData());
            lbl.setText(raw);
            translateDbAsync(raw, lbl::setText);
        };

        apply.run();
        com.advisora.utils.i18n.LangBus.localeProperty()
                .addListener((obs, o, n) -> apply.run());
    }

    private void bindDbTextWithPrefix(Label lbl, String prefixKey, String rawDbText) {
        lbl.setUserData(rawDbText);

        Runnable apply = () -> {
            String prefix = com.advisora.utils.i18n.I18n.tr(prefixKey);
            String raw = safe((String) lbl.getUserData());

            if (raw.isBlank() || raw.equals("-")) {
                lbl.setText(prefix + " -");
                return;
            }

            // raw immediately
            lbl.setText(prefix + " " + raw);
            // translated
            translateDbAsync(raw, tr -> lbl.setText(prefix + " " + tr));
        };

        apply.run();
        com.advisora.utils.i18n.LangBus.localeProperty()
                .addListener((obs, o, n) -> apply.run());
    }

    private void bindI18n(Control c, String key) {
        Runnable apply = () -> {
            String t = com.advisora.utils.i18n.I18n.tr(key);
            if (c instanceof Labeled l) l.setText(t);
            else if (c instanceof TextInputControl ti) ti.setPromptText(t);
        };
        apply.run();
        com.advisora.utils.i18n.LangBus.localeProperty()
                .addListener((obs, o, n) -> apply.run());
    }

    // =========================
    // Card building
    // =========================
    private VBox buildCard(Project p) {

        // --------------------
        // TITLE (DB -> translate)
        // --------------------
        String rawTitle =
                (SessionContext.isGerant() || SessionContext.getCurrentRole() == UserRole.ADMIN)
                        ? "#" + p.getIdProj() + " - " + safe(p.getTitleProj())
                        : safe(p.getTitleProj());

        Label title = new Label();
        title.getStyleClass().add("card-title");
        bindDbText(title, rawTitle);

        HBox head = new HBox(10, title);

        // --------------------
        // DESC (DB -> translate)
        // --------------------
        Label desc = new Label();
        desc.setWrapText(true);
        desc.getStyleClass().add("card-desc");
        bindDbText(desc, safe(p.getDescriptionProj()));

        // --------------------
        // META (prefix i18n + DB values)
        // --------------------
        Label meta = new Label();
        meta.getStyleClass().add("card-meta");

        String rawType = nullToDash(p.getTypeProj());
        String rawClient = (SessionContext.isManager() || SessionContext.getCurrentRole() == UserRole.ADMIN)
                ? safe(clientNameForProject(p))
                : "";

        meta.setUserData(List.of(rawType, rawClient, String.valueOf(p.getBudgetProj())));

        Runnable metaApply = () -> {
            @SuppressWarnings("unchecked")
            List<String> data = (List<String>) meta.getUserData();

            String typeRaw = safe(data.get(0));
            String clientRaw = safe(data.get(1));
            String budgetVal = safe(data.get(2));

            String typePrefix   = com.advisora.utils.i18n.I18n.tr("project.meta.type");
            String budgetPrefix = com.advisora.utils.i18n.I18n.tr("project.meta.budget");
            String clientPrefix = com.advisora.utils.i18n.I18n.tr("project.meta.client");

            // raw immediately
            String base = typePrefix + " " + typeRaw + "   |   " + budgetPrefix + " " + budgetVal;
            if (SessionContext.isManager() || SessionContext.getCurrentRole() == UserRole.ADMIN) {
                base += "   |   " + clientPrefix + " " + clientRaw;
            }
            meta.setText(base);

            // translate type & client
            translateDbAsync(typeRaw, trType -> {
                String txt = typePrefix + " " + trType + "   |   " + budgetPrefix + " " + budgetVal;

                if (SessionContext.isManager() || SessionContext.getCurrentRole() == UserRole.ADMIN) {
                    translateDbAsync(clientRaw, trClient -> meta.setText(txt + "   |   " + clientPrefix + " " + trClient));
                } else {
                    meta.setText(txt);
                }
            });
        };

        metaApply.run();
        com.advisora.utils.i18n.LangBus.localeProperty().addListener((obs, o, n) -> metaApply.run());

        // --------------------
        // LEFT column
        // --------------------
        VBox left = new VBox(8, desc, meta);
        left.getStyleClass().add("card-left");
        HBox.setHgrow(left, Priority.ALWAYS);

        if (p.getStateProj() == ProjectStatus.PENDING) {
            Label pendingProgress = new Label();
            pendingProgress.getStyleClass().add("card-meta");

            Runnable ppApply = () -> pendingProgress.setText(
                    com.advisora.utils.i18n.I18n.tr("project.progress") + " " +
                            com.advisora.utils.i18n.I18n.tr("project.progress.pending")
            );
            ppApply.run();
            com.advisora.utils.i18n.LangBus.localeProperty().addListener((obs, o, n) -> ppApply.run());

            left.getChildren().add(pendingProgress);

            ProjectAcceptanceEstimate estimate = pendingEstimates.get(p.getIdProj());
            if (estimate != null) left.getChildren().add(buildEstimateBox(estimate));

        } else {
            double progress = Math.max(0, Math.min(100, p.getAvancementProj())) / 100.0;
            ProgressBar bar = new ProgressBar(progress);
            bar.setMaxWidth(Double.MAX_VALUE);

            Label progressLabel = new Label();
            progressLabel.getStyleClass().add("card-meta");

            Runnable plApply = () -> progressLabel.setText(
                    com.advisora.utils.i18n.I18n.tr("project.progress") + " " +
                            String.format(Locale.US, "%.0f%%", p.getAvancementProj())
            );
            plApply.run();
            com.advisora.utils.i18n.LangBus.localeProperty().addListener((obs, o, n) -> plApply.run());

            left.getChildren().addAll(bar, progressLabel);
        }

        // --------------------
        // BADGES (status + PBS)
        // --------------------
        boolean showPbsBadge = p.getStateProj() != ProjectStatus.PENDING && p.getStateProj() != ProjectStatus.REFUSED;

        Label statusBadge = new Label();
        statusBadge.getStyleClass().addAll("status-badge", statusClassFor(p.getStateProj()));

        Runnable statusApply = () -> {
            // map status to i18n key instead of enum.name()
            String key = switch (p.getStateProj()) {
                case ACCEPTED -> "project.status.accepted";
                case REFUSED  -> "project.status.refused";
                case ARCHIVED -> "project.status.archived";
                case PENDING  -> "project.status.pending";
                default       -> "project.status.pending";
            };
            statusBadge.setText(com.advisora.utils.i18n.I18n.tr(key));
        };
        statusApply.run();
        com.advisora.utils.i18n.LangBus.localeProperty().addListener((obs, o, n) -> statusApply.run());

        VBox badgeBox = new VBox(8, statusBadge);
        badgeBox.getStyleClass().add("badge-box");
        badgeBox.setMinWidth(Region.USE_PREF_SIZE);

        if (showPbsBadge) {
            ProjectBadgeScore badgeScore = projectBadges.get(p.getIdProj());

            Label pbsBadge = new Label(formatPbsLabel(badgeScore));
            pbsBadge.getStyleClass().add("pbs-badge");
            pbsBadge.getStyleClass().add(pbsClassFor(badgeScore == null ? null : badgeScore.getBadge()));

            pbsBadge.setOnMouseClicked(e -> onBadgeDetails(p, badgeScore));
            statusBadge.setOnMouseClicked(e -> onBadgeDetails(p, badgeScore));

            badgeBox.getChildren().add(pbsBadge);
        }

        // --------------------
        // MID (depends on role)
        // --------------------
        HBox mid;

        if (SessionContext.isClient()) {
            VBox right = buildStrategiesPanel(p);
            right.getStyleClass().add("card-right");

            Region divider = new Region();
            divider.getStyleClass().add("card-divider");

            mid = new HBox(16, left, badgeBox, divider, right);
        } else {
            mid = new HBox(16, left, badgeBox);
        }
        mid.getStyleClass().add("card-mid");

        // --------------------
        // ACTIONS row
        // --------------------
        HBox actionRow = new HBox(8);
        actionRow.getStyleClass().add("card-actions");

        if (!SessionContext.isClient() && p.getStateProj() == ProjectStatus.ACCEPTED) {
            Button recommendBtn = new Button();
            bindI18n(recommendBtn, "project.action.recommend");
            recommendBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> recommendBusy));
            recommendBtn.getStyleClass().add("btn-ghost");
            recommendBtn.setOnAction(e -> runRecommendation(p));
            actionRow.getChildren().add(recommendBtn);
        }

        if (p.getStateProj() == ProjectStatus.PENDING) {
            Label pendingLabel = new Label();
            pendingLabel.getStyleClass().add("btn-ghost");
            bindI18n(pendingLabel, "project.action.pendingDecision");
            actionRow.getChildren().add(pendingLabel);
        } else {
            Button currentDecision = new Button();
            bindI18n(currentDecision, "project.action.currentDecision");
            currentDecision.getStyleClass().add("btn-ghost");
            currentDecision.setOnAction(e -> onShowCurrentDecision(p));
            actionRow.getChildren().add(currentDecision);
        }

        if (p.getStateProj() == ProjectStatus.ACCEPTED) {
            Button tasks = new Button();
            bindI18n(tasks, "project.action.tasks");
            tasks.getStyleClass().add("btn-ghost");
            tasks.setOnAction(e -> onOpenTasks(p));
            actionRow.getChildren().add(tasks);
        }

        Button news = new Button();
        bindI18n(news, "project.action.news");
        news.getStyleClass().add("btn-ghost");
        news.setOnAction(e -> onOpenNews(p));
        actionRow.getChildren().add(news);

        boolean canEditDelete =
                SessionContext.getCurrentRole() == UserRole.ADMIN
                        || (SessionContext.isClient() && p.getIdClient() == SessionContext.getCurrentUserId());

        if (canEditDelete) {
            Button edit = new Button();
            bindI18n(edit, "project.action.edit");
            edit.getStyleClass().add("btn-ghost");
            edit.setOnAction(e -> onEditProject(p));

            Button delete = new Button();
            bindI18n(delete, "project.action.delete");
            delete.getStyleClass().add("btn-ghost");
            delete.setOnAction(e -> onDeleteProject(p));

            actionRow.getChildren().addAll(edit, delete);
        }

        if (SessionContext.isManager() || SessionContext.getCurrentRole() == UserRole.ADMIN) {
            Button decide = new Button();
            bindI18n(decide, "project.action.decide");
            decide.getStyleClass().add("btn-primary");
            decide.setOnAction(e -> onDecideProject(p));
            actionRow.getChildren().add(decide);
        }

        VBox card = new VBox(10, head, mid, actionRow);
        card.getStyleClass().add("project-card");
        return card;
    }


    private VBox buildStrategiesPanel(Project p) {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("strategies-panel");
        // Title shown once.
        HBox titleRow = new HBox();
        Label title = new Label("strategies proposees :");
        title.getStyleClass().add("strategies-title");
        Runnable stApply = () -> title.setText(com.advisora.utils.i18n.I18n.tr("project.strategies.title"));
        stApply.run();
        com.advisora.utils.i18n.LangBus.localeProperty().addListener((obs,o,n)-> stApply.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);



        titleRow.getChildren().addAll(title, spacer);
        panel.getChildren().add(titleRow);

        // ... then load strategies and add buildStrategyRow(...)
        List<Strategie> strategies = strategyService.getByProject(p.getIdProj());

        if (strategies == null || strategies.isEmpty()) {
            Label empty = new Label();
            empty.getStyleClass().add("card-meta");
            bindI18n(empty, "project.strategies.empty");
            panel.getChildren().add(empty);
            return panel;
        }

        for (Strategie s : strategies) {
            if (s.getStatut() == StrategyStatut.ACCEPTEE || s.getStatut() == StrategyStatut.REFUSEE) continue;
            panel.getChildren().add(buildStrategyRow(p, s, panel));
        }

        if (panel.getChildren().size() == 1) { // only titleRow
            Label empty = new Label("Aucune strategie.");
            empty.getStyleClass().add("card-meta");
            panel.getChildren().add(empty);
        }

        return panel;
    }




    private HBox buildStrategyRow(Project p, Strategie s, VBox panel) {

        Label name = new Label(safe(s.getNomStrategie()));

        name.getStyleClass().add("strategy-name");
        bindDbText(name, safe(s.getNomStrategie()));
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setWrapText(false);
        HBox.setHgrow(name, Priority.ALWAYS);

        Button ok = new Button();
        ok.getStyleClass().add("chip-ok");
        bindI18n(ok, "project.strategy.accept");
        ok.setMinWidth(80);
        ok.setPrefWidth(90);
        Button no = new Button();
        no.getStyleClass().add("chip-no");
        bindI18n(no, "project.strategy.refuse");
        no.setMinWidth(80);
        no.setPrefWidth(90);




        HBox actions = new HBox(8, ok, no);
        actions.getStyleClass().add("strategy-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE); // important: don't shrink buttons


        HBox row = new HBox(12, name, actions);
        row.getStyleClass().add("strategy-row");
        row.setAlignment(Pos.CENTER_LEFT);

        ok.setOnAction(e -> confirmAndApplyStrategyDecision(s, true, panel, row));
        no.setOnAction(e -> confirmAndApplyStrategyDecision(s, false, panel, row));

        // Click only on the name to open dialog (so buttons still work cleanly)
        name.setOnMouseClicked(e -> onStrategyClick(s));

        return row;
    }


    private void onStrategyClick(Strategie s) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/strategie/StrategieInfoDialog.fxml"));
            Parent content = loader.load();

            com.advisora.GUI.Strategie.StrategieInfoDialogController controller = loader.getController();
            controller.setOnClose(this::closeDialog);
            controller.initWithStrategie(s);

            enableDrag(controller.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible de charger la strategie: " + ex.getMessage());
        }
    }


    private void showDialog(Parent content) {
        modalBox.setTranslateX(0);
        modalBox.setTranslateY(0);
        if (content instanceof Region r) {
            r.setMaxWidth(Region.USE_PREF_SIZE);
            r.setMaxHeight(Region.USE_PREF_SIZE);
        }
        modalBox.getChildren().setAll(content);
        overlay.setManaged(true);
        overlay.setVisible(true);
    }

    @FXML
    private void closeDialog() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        modalBox.getChildren().clear();
        modalBox.setTranslateX(0);
        modalBox.setTranslateY(0);
    }


    private void enableDrag(Node handle, Node draggable) {
        handle.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX() - draggable.getTranslateX();
            dragOffsetY = e.getSceneY() - draggable.getTranslateY();
        });
        handle.setOnMouseDragged(e -> {
            draggable.setTranslateX(e.getSceneX() - dragOffsetX);
            draggable.setTranslateY(e.getSceneY() - dragOffsetY);
        });
    }
    private void refresh() {
        // refresh the whole list/cards from DB (projects + strategies are loaded per card)
        loadProjectsFromService();
    }



    private void confirmAndApplyStrategyDecision(Strategie s, boolean accepted, VBox panel, HBox row) {
        String choice = accepted ? "OK" : "NON";

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Confirmer la decision \"" + choice + "\" pour la strategie :\n" + safe(s.getNomStrategie()) + " ?",
                ButtonType.YES,
                ButtonType.NO
        );
        confirm.setHeaderText("Confirmation");

        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            if (accepted) {
                strategyService.applyDecision(s.getId(), true, null, true);
            } else {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Justification de refus");
                dialog.setHeaderText("Strategie refusee");
                dialog.setContentText("Veuillez fournir une raison de refus:");

                Optional<String> result = dialog.showAndWait();

                if (result.isPresent() && !result.get().trim().isEmpty()) {
                    strategyService.applyDecision(s.getId(), false, result.get(), true);
                } else {
                    new Alert(Alert.AlertType.WARNING, "Le refus necessite une justification.", ButtonType.OK).showAndWait();
                    return; // Important: do not remove row if not refused.
                }
            }




            panel.getChildren().remove(row);

            if (panel.getChildren().size() == 1) {
                Label empty = new Label("Aucune strategie.");
                empty.getStyleClass().add("card-meta");
                panel.getChildren().add(empty);
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }

    }



    // =========================
    // Actions
    // =========================
    private void onShowCurrentDecision(Project project) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/decision/DecisionCurrent.fxml"));
            Parent root = loader.load();
            DecisionCurrentController controller = loader.getController();
            controller.initWithProject(project);
            openModal(root, "Decision actuelle");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void onOpenTasks(Project project) {
        if (project == null) return;
        if (project.getStateProj() != ProjectStatus.ACCEPTED) {
            showError("Todo List disponible uniquement pour les projets acceptes.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/ProjectTaskPage.fxml"));
            Parent root = loader.load();
            ProjectTaskPageController controller = loader.getController();
            controller.initWithProject(project);
            if (contentNavigator != null) {
                controller.setOnBack(() -> {
                    try {
                        FXMLLoader projectLoader = new FXMLLoader(getClass().getResource("/views/project/ProjectList.fxml"));
                        Parent projectRoot = projectLoader.load();
                        ProjectListController projectController = projectLoader.getController();
                        projectController.setContentNavigator(contentNavigator);
                        contentNavigator.accept(projectRoot);
                    } catch (Exception ex) {
                        showError(ex.getMessage());
                    }
                });
                contentNavigator.accept(root);
            } else {
                openModal(root, "Project Tasks");
                loadProjectsFromService();
            }
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void onOpenNews(Project project) {
        if (project == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/ProjectNewsPanel.fxml"));
            Parent content = loader.load();
            NewsController controller = loader.getController();
            controller.setOnClose(this::closeDialog);
            controller.initWithProject(project, newsService);
            showDialog(content);
        } catch (Exception e) {
            showError("Unable to open News panel: " + e.getMessage());
        }
    }

    @FXML
    private void onAdd() {
        if (!(SessionContext.isClient() || SessionContext.getCurrentRole() == UserRole.ADMIN)) {
            showError("Only CLIENT or ADMIN can create projects.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/ProjectForm.fxml"));
            Parent root = loader.load();
            ProjectFormController controller = loader.getController();
            controller.initForCreate();
            openModal(root, "Add Project");
            loadProjectsFromService();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void onEditProject(Project selected) {
        if (selected == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/ProjectForm.fxml"));
            Parent root = loader.load();
            ProjectFormController controller = loader.getController();
            controller.initForEdit(selected);
            openModal(root, "Edit Project");
            loadProjectsFromService();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void onDeleteProject(Project selected) {
        if (selected == null) return;
        try {
            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Voulez-vous supprimer le projet \"" + safe(selected.getTitleProj()) + "\" ?",
                    ButtonType.YES,
                    ButtonType.NO
            );
            confirm.setHeaderText("Confirmer la suppression");
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }
            projectService.delete(selected.getIdProj());
            loadProjectsFromService();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void onDecideProject(Project selected) {
        if (selected == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/decision/DecisionForm.fxml"));
            Parent root = loader.load();
            DecisionFormController controller = loader.getController();
            controller.initWithProjectId(selected.getIdProj());
            openModal(root, "Decider du projet");
            loadProjectsFromService();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // =========================
    // Data + filters
    // =========================
    private void loadProjectsFromService() {
        try {
            List<Project> projects;
            if (SessionContext.isClient()) {
                projects = projectService.getByClient(SessionContext.getCurrentUserId());
                dashboardData = statsService.getForManager();
                clientNamesById = new HashMap<>();
            } else {
                projects = projectService.getAll();
                dashboardData = statsService.getForManager();
                loadClientNamesMap();
            }
            baseProjects.setAll(projects);
            List<Project> pending = projects.stream().filter(pr -> pr.getStateProj() == ProjectStatus.PENDING).collect(Collectors.toList());
            pendingEstimates = acceptanceService.estimateForPending(pending);
            projectBadges = new HashMap<>(badgeService.computeForProjects(projects));
            applyFilters();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void applyFilters() {
        String search = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        ProjectStatus statusFilter = getSelectedStatusFilter();

        List<Project> filtered = baseProjects.stream()
                .filter(p -> statusFilter == null || p.getStateProj() == statusFilter)
                .filter(p -> search.isBlank() || matchesSearch(p, search))
                .sorted(currentComparator)
                .collect(Collectors.toList());

        lastVisibleProjects = new ArrayList<>(filtered);
        projectList.setItems(FXCollections.observableArrayList(filtered));
    }

    private boolean matchesSearch(Project p, String search) {
        return (p.getTitleProj() != null && p.getTitleProj().toLowerCase(Locale.ROOT).contains(search))
                || (p.getDescriptionProj() != null && p.getDescriptionProj().toLowerCase(Locale.ROOT).contains(search))
                || (p.getTypeProj() != null && p.getTypeProj().toLowerCase(Locale.ROOT).contains(search));
    }

    private void loadClientNamesMap() {
        Map<Integer, String> names = new HashMap<>();
        for (User user : userService.afficher()) {
            if (user == null) continue;
            int id = user.getId();
            if (id <= 0) continue;
            String fullName = ((user.getPrenom() == null ? "" : user.getPrenom().trim()) + " " +
                    (user.getNom() == null ? "" : user.getNom().trim())).trim();
            if (fullName.isBlank()) {
                fullName = "Client #" + id;
            }
            names.put(id, fullName);
        }
        clientNamesById = names;
    }

    private String clientNameForProject(Project project) {
        if (project == null || project.getIdClient() <= 0) return "-";
        return clientNamesById.getOrDefault(project.getIdClient(), "Client #" + project.getIdClient());
    }

    private ProjectStatus getSelectedStatusFilter() {
        if (SessionContext.isClient()) return null;
        if (tabPending.isSelected()) return ProjectStatus.PENDING;
        if (tabValid.isSelected()) return ProjectStatus.ACCEPTED;
        if (tabRefused.isSelected()) return ProjectStatus.REFUSED;
        return null;
    }

    @FXML private void onTabAll()     { applyFilters(); }
    @FXML private void onTabValid()   { applyFilters(); }
    @FXML private void onTabPending() { applyFilters(); }
    @FXML private void onTabRefused() { applyFilters(); }
    @FXML private void onSearch()     { applyFilters(); }

    @FXML
    private void onOpenTop10() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/Top10Dialog.fxml"));
            Parent root = loader.load();
            URL cssUrl = getClass().getResource("/views/style/top10.css");
            if (cssUrl != null) {
                root.getStylesheets().add(cssUrl.toExternalForm());
            }
            openModal(root, "Top 10");
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible d'ouvrir Top 10.\nCause: " + rootCauseMessage(ex));
        }
    }

    @FXML
    private void onOpenEstimation() {
        if (!(SessionContext.isClient() || SessionContext.getCurrentRole() == UserRole.ADMIN)) {
            showError("Only CLIENT or ADMIN can run estimation.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/EstimationDialog.fxml"));
            Parent root = loader.load();
            EstimationDialogController controller = loader.getController();
            if (contentNavigator != null) {
                controller.setOnBack(() -> {
                    try {
                        FXMLLoader projectLoader = new FXMLLoader(getClass().getResource("/views/project/ProjectList.fxml"));
                        Parent projectRoot = projectLoader.load();
                        ProjectListController projectController = projectLoader.getController();
                        projectController.setContentNavigator(contentNavigator);
                        contentNavigator.accept(projectRoot);
                    } catch (Exception ex) {
                        showError(ex.getMessage());
                    }
                });
                contentNavigator.accept(root);
            } else {
                openModal(root, "Estimation");
            }
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onOpenStats() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/project/ProjectStats.fxml"));
            Parent root = loader.load();
            ProjectStatsController controller = loader.getController();
            controller.init(dashboardData, SessionContext.getCurrentRole());
            if (contentNavigator != null) {
                controller.setOnBack(() -> {
                    try {
                        FXMLLoader projectLoader = new FXMLLoader(getClass().getResource("/views/project/ProjectList.fxml"));
                        Parent projectRoot = projectLoader.load();
                        ProjectListController projectController = projectLoader.getController();
                        projectController.setContentNavigator(contentNavigator);
                        contentNavigator.accept(projectRoot);
                    } catch (Exception ex) {
                        showError(ex.getMessage());
                    }
                });
                contentNavigator.accept(root);
            } else {
                Stage stage = new Stage();
                stage.setTitle("Statistiques projets");
                stage.initOwner(projectList.getScene().getWindow());
                SceneThemeApplier.setScene(stage, root);
                stage.setMinWidth(1100);
                stage.setMinHeight(700);
                stage.show();
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void onExportPdf() {
        try {
            if (SessionContext.isClient()) {
                dashboardData = statsService.getForClient(SessionContext.getCurrentUserId());
            } else {
                dashboardData = statsService.getForManager();
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter rapport projets (PDF)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            String role = SessionContext.getCurrentRole() == null ? "UNKNOWN" : SessionContext.getCurrentRole().name();
            chooser.setInitialFileName("projets_" + role + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".pdf");
            File selected = chooser.showSaveDialog(projectList.getScene().getWindow());
            if (selected == null) {
                return;
            }
            File output = selected.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? selected
                    : (selected.getParentFile() == null
                    ? new File(selected.getName() + ".pdf")
                    : new File(selected.getParentFile(), selected.getName() + ".pdf"));

            List<Project> visible = new ArrayList<>(projectList.getItems());
            File out = pdfExportService.exportVisibleProjectsReport(
                    visible,
                    dashboardData,
                    SessionContext.getCurrentRole(),
                    getCurrentFilterLabel(),
                    txtSearch == null ? "" : txtSearch.getText(),
                    output
            );
            ensureValidPdf(out);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setHeaderText("Export PDF termine");
            ok.setContentText("Fichier genere:\n" + out.getAbsolutePath());
            ok.showAndWait();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
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

    private String getCurrentFilterLabel() {
        if (SessionContext.isClient()) {
            return "Mes projets";
        }
        if (tabPending != null && tabPending.isSelected()) return "En attente";
        if (tabValid != null && tabValid.isSelected()) return "Valide";
        if (tabRefused != null && tabRefused.isSelected()) return "Refuse";
        return "Tous les projets";
    }

    @FXML
    private void onOpenUsers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Admin/admin.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) projectList.getScene().getWindow();
            SceneThemeApplier.setScene(stage, root);
            stage.setTitle("Advisora - Admin Dashboard");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onSortBudgetAsc() {
        currentComparator = Comparator.comparing(Project::getBudgetProj);
        applyFilters();
    }

    @FXML
    private void onSortBudgetDesc() {
        currentComparator = Comparator.comparing(Project::getBudgetProj).reversed();
        applyFilters();
    }

    @FXML
    private void onSortAvancementAsc() {
        currentComparator = Comparator.comparing(Project::getAvancementProj);
        applyFilters();
    }

    @FXML
    private void onSortAvancementDesc() {
        currentComparator = Comparator.comparing(Project::getAvancementProj).reversed();
        applyFilters();
    }

    private VBox buildEstimateBox(ProjectAcceptanceEstimate estimate) {
        Label title = new Label("strategies proposees :");
        title.getStyleClass().add("estimate-title");

        ProgressBar bar = new ProgressBar(Math.max(0, Math.min(100, estimate.getScorePercent())) / 100.0);
        bar.getStyleClass().add("estimate-bar");
        bar.setMaxWidth(Double.MAX_VALUE);

        Label score = new Label("Taux estime: " + estimate.getScorePercent() + "% - " + scoreLabel(estimate.getScorePercent()));
        score.getStyleClass().add("estimate-score");

        VBox reasons = new VBox(4);
        for (String r : estimate.getReasons()) {
            Label row = new Label("- " + r);
            row.getStyleClass().add("estimate-reason");
            reasons.getChildren().add(row);
        }

        VBox box = new VBox(6, title, bar, score, reasons);
        box.getStyleClass().add("estimate-box");
        return box;
    }

    private String scoreLabel(int score) {
        if (score >= 70) return "Bonne probabilite";
        if (score >= 40) return "Probabilite moyenne";
        return "Probabilite faible";
    }
    // =========================
    // Helpers
    // =========================
    private int compareTsDescNullSafe(Timestamp a, Timestamp b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }



    private String statusClassFor(ProjectStatus status) {
        if (status == null) return "status-pending";
        return switch (status) {
            case ACCEPTED -> "status-accepted";
            case REFUSED -> "status-refused";
            case ARCHIVED -> "status-archived";
            default -> "status-pending";
        };
    }

    private String formatPbsLabel(ProjectBadgeScore score) {
        if (score == null || score.getBadge() == null || score.getBadge().isBlank()) {
            return "-";
        }
        return badgeLabel(score.getBadge());
    }

    private String pbsClassFor(String badge) {
        if (badge == null) return "pbs-bronze";
        return switch (badge.trim().toUpperCase(Locale.ROOT)) {
            case "ARGENT" -> "pbs-argent";
            case "OR" -> "pbs-or";
            case "PLATINE" -> "pbs-platine";
            default -> "pbs-bronze";
        };
    }

    private String badgeLabel(String badge) {
        if (badge == null || badge.isBlank()) return "BRONZE";
        return badge.trim().toUpperCase(Locale.ROOT);
    }

    private void onBadgeDetails(Project project, ProjectBadgeScore score) {
        if (project == null || score == null) return;

        String badge = score.getBadge() == null ? "-" : score.getBadge();
        StringBuilder details = new StringBuilder();
        details.append("Projet: ").append(safe(project.getTitleProj())).append("\n");
        details.append("Badge: ").append(badgeLabel(badge)).append("\n");
        details.append("Note globale: ").append(String.format(Locale.US, "%.1f", score.getPbs())).append("/100\n\n");

        details.append("Pourquoi ce badge (simple):\n");
        details.append("- Avancement dans le temps: ")
                .append(componentComment(score.getTemporalScore()))
                .append(" (").append(String.format(Locale.US, "%.0f", score.getTemporalScore())).append("/100)\n");
        details.append("  Exemple: si 50% du temps est passe et vous etes a 60% d'avancement => bon point.\n");
        details.append("- Fiabilite: ")
                .append(componentComment(score.getReliabilityScore()))
                .append(" (").append(String.format(Locale.US, "%.0f", score.getReliabilityScore())).append("/100)\n");
        details.append("  Exemple: si le projet a deja ete refuse une fois => forte baisse.\n");
        details.append("- Suivi regulier: ")
                .append(componentComment(score.getRegularityScore()))
                .append(" (").append(String.format(Locale.US, "%.0f", score.getRegularityScore())).append("/100)\n");
        details.append("  Exemple: mises a jour chaque semaine => mieux que tout faire le dernier jour.\n");
        details.append("- Stabilite: ")
                .append(componentComment(score.getStabilityScore()))
                .append(" (").append(String.format(Locale.US, "%.0f", score.getStabilityScore())).append("/100)\n");
        details.append("  Exemple: souvent en retard puis a jour puis en retard => score plus bas.\n\n");

        details.append("Comment lire le badge:\n");
        details.append("- Bronze (<40): projet en difficulte\n");
        details.append("- Argent (40-64): projet moyen\n");
        details.append("- Or (65-84): bon projet\n");
        details.append("- Platine (85+): excellent projet\n");
        if (score.isHadRefusalHistory()) {
            details.append("- Regle speciale: deja refuse => pas de Platine.\n");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Detail badge");
        alert.setHeaderText("Explication du badge du projet");
        alert.setContentText(details.toString());
        alert.showAndWait();
    }

    private String componentComment(double value) {
        if (value >= 80.0) return "fort";
        if (value >= 50.0) return "moyen";
        return "faible";
    }

    private void openModal(Parent root, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        SceneThemeApplier.setScene(stage, root);
        stage.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        if (msg == null || msg.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return current.getClass().getSimpleName() + ": " + msg;
    }


    @FXML
    private void onRecommend(ActionEvent actionEvent) {
        Project selected = projectList.getSelectionModel().getSelectedItem();
        runRecommendation(selected);
    }

    /**
     * Open the modal that asks the Gerant to either confirm or ignore the
     * generated strategy before it is inserted into the database.
     */
    private void openStrategyConfirmDialog(Strategie draft) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/strategie/StrategieInfoDialog.fxml"));
            Parent content = loader.load();

            com.advisora.GUI.Strategie.StrategieInfoDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.initWithStrategie(draft);
            // Expose two callbacks: confirm => persist; ignore => close.
            c.enableRecommendationActions(true);   // show confirm/ignore buttons in the dialog

            c.setOnConfirm(() -> {
                try {
                    // persist the strategy ONLY when the Gerant clicks Confirm
                    strategyService.confirmRecommendedStrategy(draft);
                    closeDialog();
                    refresh();                     // reload the project list cards
                    new Alert(Alert.AlertType.INFORMATION, "Strategie enregistree.", ButtonType.OK).showAndWait();
                } catch (Exception ex) {
                    showError("Enregistrement impossible: " + ex.getMessage());
                }
            });

            c.setOnIgnore(() -> closeDialog());    // no save, no refresh

            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible d'ouvrir la strategie : " + ex.getMessage());
        }
    }
    /**
     * Launches the asynchronous recommendation (calls Gemini).
     */
    private void runRecommendation(Project project) {
        if (project == null || project.getIdProj() <= 0) {
            showError("Aucun projet selectionne.");
            return;
        }
        if (recommendBusy) return;
        recommendBusy = true;

        overlay.getChildren().add(recommendSpinner);
        recommendSpinner.setVisible(true);
        overlay.setManaged(true);
        overlay.setVisible(true);

        Task<Strategie> task = new Task<>() {
            @Override
            protected Strategie call() {
                // Only build a draft (no DB insert).
                return strategyService.recommendDraftStrategy(project);
            }
        };

        task.setOnSucceeded(e -> {
            recommendBusy = false;
            hideRecommendSpinner();

            Strategie draft = task.getValue();
            if (draft == null) {
                showError("La recommandation a echoue.");
                return;
            }
            // Open confirm dialog (still not saved).
            openStrategyConfirmDialog(draft);
        });

        task.setOnFailed(e -> {
            recommendBusy = false;
            hideRecommendSpinner();

            Throwable th = task.getException();
            showError("Erreur recommandation:\n" + (th == null ? "Erreur inconnue" : rootCauseMessage(th)));
            if (th != null) th.printStackTrace();
        });

        Thread th = new Thread(task, "recommend-strategy");
        th.setDaemon(true);
        th.start();
    }

    /**
     * Show the spinner overlay.
     */
    private void showRecommendSpinner() {
        recommendSpinner.setVisible(true);
        overlay.getChildren().add(recommendSpinner);
        overlay.setManaged(true);
        overlay.setVisible(true);
    }

    /**
     * Hides the spinner overlay.
     */
    private void hideRecommendSpinner() {
        recommendSpinner.setVisible(false);
        overlay.getChildren().remove(recommendSpinner);

        // If no other modal is showing, hide the overlay
        boolean modalOpen = modalBox != null && !modalBox.getChildren().isEmpty();
        if (!modalOpen) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }
    }



}



