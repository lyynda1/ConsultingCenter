/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: GUI controller: user interactions and screen flow
*/
package com.advisora.GUI.Project;

import com.advisora.Model.Project;
import com.advisora.Services.ProjectService;
import com.advisora.Services.SessionContext;
import com.advisora.enums.ProjectStatus;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class ProjectListController implements Initializable {

    @FXML
    private Button btnNewProject;
    @FXML
    private Button btnUsers;
    @FXML
    private ToggleButton tabAll;
    @FXML
    private ToggleButton tabValid;
    @FXML
    private ToggleButton tabPending;
    @FXML
    private ToggleButton tabRefused;
    @FXML
    private TextField txtSearch;
    @FXML
    private ListView<Project> projectList;
    @FXML
    private Label lblTotalProjects;
    @FXML
    private Label lblPendingProjects;
    @FXML
    private Label lblAcceptanceRate;

    private final ProjectService projectService = new ProjectService();
    private final ObservableList<Project> baseProjects = FXCollections.observableArrayList();
    // Default sort: newest project first.
    private Comparator<Project> currentComparator = Comparator.comparing(Project::getCreatedAtProj, this::compareTsDescNullSafe);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Screen bootstrap order:
        // 1) adapt UI by role
        // 2) install card renderer for ListView
        // 3) load DB data
        setupRoleUI();
        setupCardRenderer();
        loadProjectsFromService();
    }

    private void setupRoleUI() {
        // Client can create projects.
        // Manager uses decision workflow and status tabs instead.
        boolean canCreateProject = SessionContext.isClient() || SessionContext.getCurrentRole() == UserRole.ADMIN;
        btnNewProject.setDisable(!canCreateProject);
        boolean isAdmin = SessionContext.getCurrentRole() == UserRole.ADMIN;
        btnUsers.setVisible(isAdmin);
        btnUsers.setManaged(isAdmin);

        if (!(SessionContext.isManager() || isAdmin)) {
            tabAll.setVisible(false);
            tabAll.setManaged(false);
            tabValid.setVisible(false);
            tabValid.setManaged(false);
            tabPending.setVisible(false);
            tabPending.setManaged(false);
            tabRefused.setVisible(false);
            tabRefused.setManaged(false);
        }
    }

    private void setupCardRenderer() {
        // Render each Project row as a visual "card" instead of plain text.
        projectList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(buildCard(item));
            }
        });
    }

    private VBox buildCard(Project p) {
        // Card header.
        String titleText = (SessionContext.isGerant() || SessionContext.getCurrentRole() == UserRole.ADMIN)
                ? "#" + p.getIdProj() + " - " + p.getTitleProj()
                : p.getTitleProj();
        Label title = new Label(titleText);
        title.getStyleClass().add("card-title");
        Label statusBadge = new Label(p.getStateProj() == null ? "-" : p.getStateProj().name());
        statusBadge.getStyleClass().add("status-badge");
        statusBadge.getStyleClass().add(statusClassFor(p.getStateProj()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, statusBadge);

        // Card body.
        Label desc = new Label(p.getDescriptionProj() == null ? "" : p.getDescriptionProj());
        desc.setWrapText(true);
        desc.getStyleClass().add("card-desc");

        Label meta = new Label(
                "Type: " + nullToDash(p.getTypeProj()) +
                        "   |   Budget: " + p.getBudgetProj()
        );
        meta.getStyleClass().add("card-meta");

        double progress = Math.max(0, Math.min(100, p.getAvancementProj())) / 100.0;
        ProgressBar bar = new ProgressBar(progress);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label progressLabel = new Label("Avancement: " + String.format(Locale.US, "%.0f%%", p.getAvancementProj()));
        progressLabel.getStyleClass().add("card-meta");

        HBox actionRow = new HBox(8);
        actionRow.getStyleClass().add("card-actions");

        // Action available for all users: display current decision if it exists.

        if(p.getStateProj() == ProjectStatus.PENDING) {
            Label pendingLabel = new Label("En attente de décision du manager");
            pendingLabel.getStyleClass().add("btn-ghost");
            actionRow.getChildren().add(pendingLabel);
        } else {

        Button currentDecision = new Button("Decision actuelle");
        currentDecision.getStyleClass().add("btn-ghost");
        currentDecision.setOnAction(e -> onShowCurrentDecision(p));
        actionRow.getChildren().add(currentDecision);
        }
        // Client actions: can edit/delete own project.
        if (SessionContext.isClient() || SessionContext.getCurrentRole() == UserRole.ADMIN) {
            Button edit = new Button("Edit");
            edit.getStyleClass().add("btn-ghost");
            edit.setOnAction(e -> onEditProject(p));
            Button delete = new Button("Delete");
            delete.getStyleClass().add("btn-ghost");
            delete.setOnAction(e -> onDeleteProject(p));
            actionRow.getChildren().addAll(edit, delete);
        }

        // Manager action: create decision on selected project.
        if (SessionContext.isManager() || SessionContext.getCurrentRole() == UserRole.ADMIN) {
            Button decide = new Button("Decide");
            decide.getStyleClass().add("btn-primary");
            decide.setOnAction(e -> onDecideProject(p));
            actionRow.getChildren().add(decide);
        }

        VBox card = new VBox(8, head, desc, meta, bar, progressLabel, actionRow);
        card.getStyleClass().add("project-card");
        return card;
    }

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
                    "Voulez-vous supprimer le projet \"" + selected.getTitleProj() + "\" ?",
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

    private void loadProjectsFromService() {
        try {
            List<Project> projects;
            // Client scope: only own projects. Others: full list.
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
        // Combine all active filters: search + status + current sort.
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

        if (lblTotalProjects != null) {
            lblTotalProjects.setText(String.valueOf(total));
        }
        if (lblPendingProjects != null) {
            lblPendingProjects.setText(String.valueOf(pending));
        }
        if (lblAcceptanceRate != null) {
            lblAcceptanceRate.setText(acceptanceRate);
        }
    }

    private boolean matchesSearch(Project p, String search) {
        return (p.getTitleProj() != null && p.getTitleProj().toLowerCase(Locale.ROOT).contains(search))
                || (p.getDescriptionProj() != null && p.getDescriptionProj().toLowerCase(Locale.ROOT).contains(search))
                || (p.getTypeProj() != null && p.getTypeProj().toLowerCase(Locale.ROOT).contains(search));
    }

    private ProjectStatus getSelectedStatusFilter() {
        // Tabs are manager-only. Clients always get all their own projects.
        if (SessionContext.isClient()) return null;
        if (tabPending.isSelected()) return ProjectStatus.PENDING;
        if (tabValid.isSelected()) return ProjectStatus.ACCEPTED;
        if (tabRefused.isSelected()) return ProjectStatus.REFUSED;
        return null;
    }

    @FXML
    private void onTabAll() {
        applyFilters();
    }

    @FXML
    private void onTabValid() {
        applyFilters();
    }

    @FXML
    private void onTabPending() {
        applyFilters();
    }

    @FXML
    private void onTabRefused() {
        applyFilters();
    }

    @FXML
    private void onSearch() {
        applyFilters();
    }

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
        // Recompute comparator then re-apply filters to refresh list.
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

    private int compareTsDescNullSafe(Timestamp a, Timestamp b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String statusClassFor(ProjectStatus status) {
        if (status == null) {
            return "status-pending";
        }
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
