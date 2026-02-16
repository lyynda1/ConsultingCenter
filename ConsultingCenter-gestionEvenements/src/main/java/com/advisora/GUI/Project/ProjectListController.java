/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: GUI controller: user interactions and screen flow
*/
package com.advisora.GUI.Project;

import com.advisora.GUI.Objective.ObjectiveInfoController;
import com.advisora.GUI.Strategie.StrategieInfoDialogController;
import com.advisora.Model.Objective;
import com.advisora.Model.Project;
import com.advisora.Model.Strategie;
import com.advisora.Services.ProjectService;
import com.advisora.Services.ServiceObjective;
import com.advisora.Services.ServiceStrategie;
import com.advisora.Services.SessionContext;
import com.advisora.enums.ProjectStatus;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;



public class ProjectListController implements Initializable {

    @FXML private Button btnNewProject;
    @FXML private Button btnUsers;
    @FXML private ToggleButton tabAll;
    @FXML private ToggleButton tabValid;
    @FXML private ToggleButton tabPending;
    @FXML private ToggleButton tabRefused;
    @FXML private TextField txtSearch;
    @FXML private ListView<Project> projectList;
    @FXML private Label lblTotalProjects;
    @FXML private Label lblPendingProjects;
    @FXML private Label lblAcceptanceRate;
    @FXML private StackPane overlay;
    @FXML private VBox modalBox;
    private double dragOffsetX;
    private double dragOffsetY;
    private final ObservableList<Strategie> allObs = FXCollections.observableArrayList();
    private final ObservableList<Strategie> viewObs = FXCollections.observableArrayList();

    private final ProjectService projectService = new ProjectService();
    private final ServiceStrategie strategyService = new ServiceStrategie();
    private final ServiceObjective serviceObjective = new ServiceObjective();

    private final ObservableList<Project> baseProjects = FXCollections.observableArrayList();

    // Default sort: newest first (null-safe).
    private Comparator<Project> currentComparator =
            Comparator.comparing(Project::getCreatedAtProj, this::compareTsDescNullSafe);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupRoleUI();
        setupCardRenderer();

        // Keep ListView non-null and stable
        projectList.setItems(FXCollections.observableArrayList());

        loadProjectsFromService();
    }

    // =========================
    // UI bootstrap
    // =========================
    private void setupRoleUI() {
        boolean canCreateProject = SessionContext.isClient() || SessionContext.getCurrentRole() == UserRole.ADMIN;
        btnNewProject.setDisable(!canCreateProject);

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

    // =========================
    // Card building
    // =========================
    private VBox buildCard(Project p) {

        // Title
        String titleText =
                (SessionContext.isGerant() || SessionContext.getCurrentRole() == UserRole.ADMIN)
                        ? "#" + p.getIdProj() + " - " + safe(p.getTitleProj())
                        : safe(p.getTitleProj());

        Label title = new Label(titleText);
        title.getStyleClass().add("card-title");
        HBox head = new HBox(10, title);

        // Left content
        Label desc = new Label(p.getDescriptionProj() == null ? "" : p.getDescriptionProj());
        desc.setWrapText(true);
        desc.getStyleClass().add("card-desc");

        Label meta = new Label("Type: " + nullToDash(p.getTypeProj()) + "   |   Budget: " + p.getBudgetProj());
        meta.getStyleClass().add("card-meta");

        double progress = Math.max(0, Math.min(100, p.getAvancementProj())) / 100.0;
        ProgressBar bar = new ProgressBar(progress);
        bar.setMaxWidth(Double.MAX_VALUE);

        Label progressLabel = new Label("Avancement: " + String.format(Locale.US, "%.0f%%", p.getAvancementProj()));
        progressLabel.getStyleClass().add("card-meta");

        VBox left = new VBox(8, desc, meta, bar, progressLabel);
        left.getStyleClass().add("card-left");
        HBox.setHgrow(left, Priority.ALWAYS);

        // Status badge (keep it)
        Label statusBadge = new Label(p.getStateProj() == null ? "-" : p.getStateProj().name());
        statusBadge.getStyleClass().addAll("status-badge", statusClassFor(p.getStateProj()));

        VBox badgeBox = new VBox(statusBadge);
        badgeBox.getStyleClass().add("badge-box");
        badgeBox.setMinWidth(Region.USE_PREF_SIZE);

        // ===== MID (conditional) =====
        HBox mid;
        if (SessionContext.isClient()) {
            VBox right = buildStrategiesPanel(p);
            right.getStyleClass().add("card-right");

            Region divider = new Region();
            divider.getStyleClass().add("card-divider");

            mid = new HBox(16, left, badgeBox, divider, right);
        } else {
            // No divider, no right panel => no empty space
            mid = new HBox(16, left, badgeBox);
        }
        mid.getStyleClass().add("card-mid");

        // Actions row (same logic)
        HBox actionRow = new HBox(8);
        actionRow.getStyleClass().add("card-actions");

        if (p.getStateProj() == ProjectStatus.PENDING) {
            Label pendingLabel = new Label("En attente de décision du manager");
            pendingLabel.getStyleClass().add("btn-ghost");
            actionRow.getChildren().add(pendingLabel);
        } else {
            Button currentDecision = new Button("Decision actuelle");
            currentDecision.getStyleClass().add("btn-ghost");
            currentDecision.setOnAction(e -> onShowCurrentDecision(p));
            actionRow.getChildren().add(currentDecision);
        }

        if (SessionContext.isClient() || SessionContext.getCurrentRole() == UserRole.ADMIN) {
            Button edit = new Button("Edit");
            edit.getStyleClass().add("btn-ghost");
            edit.setOnAction(e -> onEditProject(p));

            Button delete = new Button("Delete");
            delete.getStyleClass().add("btn-ghost");
            delete.setOnAction(e -> onDeleteProject(p));

            actionRow.getChildren().addAll(edit, delete);
        }

        if (SessionContext.isManager() || SessionContext.getCurrentRole() == UserRole.ADMIN) {
            Button decide = new Button("Decide");
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

        // ✅ Title shown once
        HBox titleRow = new HBox();
        Label title = new Label("STRATÉGIES PROPOSÉES :");
        title.getStyleClass().add("strategies-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);



        titleRow.getChildren().addAll(title, spacer);
        panel.getChildren().add(titleRow);

        // ... then load strategies and add buildStrategyRow(...)
        List<Strategie> strategies = strategyService.getByProject(p.getIdProj());

        if (strategies == null || strategies.isEmpty()) {
            Label empty = new Label("Aucune stratégie.");
            empty.getStyleClass().add("card-meta");
            panel.getChildren().add(empty);
            return panel;
        }

        for (Strategie s : strategies) {
            if (s.getStatut() == StrategyStatut.ACCEPTEE || s.getStatut() == StrategyStatut.REFUSEE) continue;
            panel.getChildren().add(buildStrategyRow(p, s, panel));
        }

        if (panel.getChildren().size() == 1) { // only titleRow
            Label empty = new Label("Aucune stratégie.");
            empty.getStyleClass().add("card-meta");
            panel.getChildren().add(empty);
        }

        return panel;
    }




    private HBox buildStrategyRow(Project p, Strategie s, VBox panel) {

        Label name = new Label(safe(s.getNomStrategie()));
        name.getStyleClass().add("strategy-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setWrapText(false);
        HBox.setHgrow(name, Priority.ALWAYS);

        Button ok = new Button("Accepter");
        ok.getStyleClass().add("chip-ok");
        ok.setMinWidth(80);
        ok.setPrefWidth(90);


        Button no = new Button("Refuser");
        no.getStyleClass().add("chip-no");
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
            showError("Impossible d'ouvrir detail strategie: " + ex.getMessage());
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
                "Confirmer la décision \"" + choice + "\" pour la stratégie :\n" + safe(s.getNomStrategie()) + " ?",
                ButtonType.YES,
                ButtonType.NO
        );
        confirm.setHeaderText("Confirmation");

        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            if (accepted) {
                strategyService.setDecision(s.getId(), true); // ACCEPTED keeps idProj
            } else {
                strategyService.refuseAndDetach(s.getId());   // REFUSED + remove idProj
            }

            panel.getChildren().remove(row);

            if (panel.getChildren().size() == 1) {
                Label empty = new Label("Aucune stratégie.");
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
            openModal(root, "Decide Project");
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
            } else {
                projects = projectService.getAll();
            }
            baseProjects.setAll(projects);
            updateStats(baseProjects);
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

        projectList.setItems(FXCollections.observableArrayList(filtered));
    }

    private void updateStats(List<Project> projects) {
        int total = projects == null ? 0 : projects.size();
        long pending = projects == null ? 0 : projects.stream().filter(p -> p.getStateProj() == ProjectStatus.PENDING).count();
        long accepted = projects == null ? 0 : projects.stream().filter(p -> p.getStateProj() == ProjectStatus.ACCEPTED).count();
        String acceptanceRate = total == 0 ? "0%" : Math.round((accepted * 100.0) / total) + "%";

        if (lblTotalProjects != null) lblTotalProjects.setText(String.valueOf(total));
        if (lblPendingProjects != null) lblPendingProjects.setText(String.valueOf(pending));
        if (lblAcceptanceRate != null) lblAcceptanceRate.setText(acceptanceRate);
    }

    private boolean matchesSearch(Project p, String search) {
        return (p.getTitleProj() != null && p.getTitleProj().toLowerCase(Locale.ROOT).contains(search))
                || (p.getDescriptionProj() != null && p.getDescriptionProj().toLowerCase(Locale.ROOT).contains(search))
                || (p.getTypeProj() != null && p.getTypeProj().toLowerCase(Locale.ROOT).contains(search));
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
    private void onOpenUsers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Admin/admin.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) projectList.getScene().getWindow();
            stage.setScene(new Scene(root));
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
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

    private void openModal(Parent root, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
