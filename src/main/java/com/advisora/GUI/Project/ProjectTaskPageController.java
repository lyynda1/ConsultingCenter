package com.advisora.GUI.Project;

import com.advisora.Model.projet.Project;
import com.advisora.Model.projet.Task;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.projet.TaskService;
import com.advisora.enums.TaskStatus;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ProjectTaskPageController {
    @FXML private Label lblProjectTitle;
    @FXML private Label lblProgress;
    @FXML private Label lblTodoCount;
    @FXML private Label lblInProgressCount;
    @FXML private Label lblDoneCount;
    @FXML private Label lblReadonlyHint;

    @FXML private ListView<Task> todoList;
    @FXML private ListView<Task> inProgressList;
    @FXML private ListView<Task> doneList;

    @FXML private TextField txtTaskTitle;
    @FXML private TextField txtTaskWeight;
    @FXML private ComboBox<TaskStatus> cbStatus;
    @FXML private Button btnSaveTask;
    @FXML private Button btnDeleteTask;

    private final TaskService taskService = new TaskService();
    private Project project;
    private Task selectedTask;
    private boolean canManage;
    private Runnable onBack;

    public void initWithProject(Project project) {
        this.project = project;
        this.lblProjectTitle.setText("Tasks - " + safe(project.getTitleProj()) + " (#" + project.getIdProj() + ")");

        cbStatus.setItems(FXCollections.observableArrayList(TaskStatus.values()));
        cbStatus.setValue(TaskStatus.TODO);
        setupStatusCombo();
        txtTaskWeight.setText("1");

        canManage = SessionContext.getCurrentRole() == UserRole.GERANT || SessionContext.getCurrentRole() == UserRole.ADMIN;
        setupPermissions();
        setupLists();
        reload();
    }

    private void setupPermissions() {
        txtTaskTitle.setDisable(!canManage);
        txtTaskWeight.setDisable(!canManage);
        cbStatus.setDisable(!canManage);
        btnSaveTask.setDisable(!canManage);
        btnDeleteTask.setDisable(!canManage);

        lblReadonlyHint.setVisible(!canManage);
        lblReadonlyHint.setManaged(!canManage);
    }

    private void setupLists() {
        installCellFactory(todoList);
        installCellFactory(inProgressList);
        installCellFactory(doneList);

        todoList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                inProgressList.getSelectionModel().clearSelection();
                doneList.getSelectionModel().clearSelection();
                onTaskSelected(newV);
            }
        });

        inProgressList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                todoList.getSelectionModel().clearSelection();
                doneList.getSelectionModel().clearSelection();
                onTaskSelected(newV);
            }
        });

        doneList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                todoList.getSelectionModel().clearSelection();
                inProgressList.getSelectionModel().clearSelection();
                onTaskSelected(newV);
            }
        });
    }

    private void installCellFactory(ListView<Task> listView) {
        listView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label meta = new Label(statusLabel(item.getStatus()) + "  -  importance=" + item.getWeight());
                meta.getStyleClass().add("task-card-meta");

                Label title = new Label(safe(item.getTitle()));
                title.getStyleClass().add("task-card-title");
                title.setWrapText(true);

                VBox card = new VBox(8, meta, title);
                card.getStyleClass().add("task-card");

                setText(null);
                setGraphic(card);
            }
        });
    }

    private void setupStatusCombo() {
        StringConverter<TaskStatus> converter = new StringConverter<>() {
            @Override
            public String toString(TaskStatus status) {
                return statusLabel(status);
            }

            @Override
            public TaskStatus fromString(String string) {
                return switch (string) {
                    case "A faire" -> TaskStatus.TODO;
                    case "En cours" -> TaskStatus.IN_PROGRESS;
                    case "Termine" -> TaskStatus.DONE;
                    default -> TaskStatus.TODO;
                };
            }
        };

        cbStatus.setConverter(converter);
        cbStatus.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TaskStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : statusLabel(item));
            }
        });
        cbStatus.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(TaskStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : statusLabel(item));
            }
        });
    }

    private void onTaskSelected(Task task) {
        selectedTask = task;
        if (!canManage) {
            return;
        }
        txtTaskTitle.setText(task.getTitle());
        txtTaskWeight.setText(String.valueOf(task.getWeight()));
        cbStatus.setValue(task.getStatus());
    }

    @FXML
    private void onSaveTask() {
        try {
            Task t = selectedTask == null ? new Task() : selectedTask;
            t.setProjectId(project.getIdProj());
            t.setTitle(required(txtTaskTitle.getText(), "title requis"));
            t.setWeight(parseWeight(txtTaskWeight.getText()));
            t.setStatus(cbStatus.getValue() == null ? TaskStatus.TODO : cbStatus.getValue());

            if (selectedTask == null) {
                taskService.addTask(t);
            } else {
                taskService.updateTask(t);
            }

            clearForm();
            reload();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onDeleteTask() {
        if (selectedTask == null) {
            return;
        }
        try {
            taskService.deleteTask(selectedTask.getId());
            clearForm();
            reload();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onNewTask() {
        clearForm();
    }

    @FXML
    private void onClose() {
        if (onBack != null) {
            onBack.run();
            return;
        }
        Stage stage = (Stage) lblProjectTitle.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void onBackToProjects() {
        if (onBack != null) {
            onBack.run();
            return;
        }
        onClose();
    }

    private void reload() {
        List<Task> all = taskService.getByProject(project.getIdProj());
        setColumnItems(all);

        double progress = taskService.computeProjectProgress(project.getIdProj());
        lblProgress.setText(String.format(Locale.US, "Progress: %.0f%%", progress));

        lblTodoCount.setText(String.valueOf(todoList.getItems().size()));
        lblInProgressCount.setText(String.valueOf(inProgressList.getItems().size()));
        lblDoneCount.setText(String.valueOf(doneList.getItems().size()));
    }

    private void setColumnItems(List<Task> all) {
        ObservableList<Task> todo = FXCollections.observableArrayList(
                all.stream().filter(t -> t.getStatus() == TaskStatus.TODO).collect(Collectors.toList())
        );
        ObservableList<Task> inProgress = FXCollections.observableArrayList(
                all.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).collect(Collectors.toList())
        );
        ObservableList<Task> done = FXCollections.observableArrayList(
                all.stream().filter(t -> t.getStatus() == TaskStatus.DONE).collect(Collectors.toList())
        );

        todoList.setItems(todo);
        inProgressList.setItems(inProgress);
        doneList.setItems(done);
    }

    private void clearForm() {
        selectedTask = null;
        todoList.getSelectionModel().clearSelection();
        inProgressList.getSelectionModel().clearSelection();
        doneList.getSelectionModel().clearSelection();
        txtTaskTitle.clear();
        txtTaskWeight.setText("1");
        cbStatus.setValue(TaskStatus.TODO);
    }

    private int parseWeight(String value) {
        try {
            int w = Integer.parseInt(required(value, "importance requise"));
            if (w < 1) {
                throw new IllegalArgumentException("importance doit etre >= 1");
            }
            return w;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("importance doit etre un entier");
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String statusLabel(TaskStatus status) {
        if (status == null) return "A faire";
        return switch (status) {
            case TODO -> "A faire";
            case IN_PROGRESS -> "En cours";
            case DONE -> "Termine";
        };
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
