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
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ProjectFormController {

    @FXML
    private TextField txtTitle;
    @FXML
    private TextArea txtDescription;
    @FXML
    private TextField txtBudget;
    @FXML
    private TextField txtType;
    @FXML
    private TextField txtAvancement;
    @FXML
    private Button btnDelete;

    private final ProjectService projectService = new ProjectService();
    private Project currentProject;
    // false = create mode, true = edit mode
    private boolean editMode;

    public void initForCreate() {
        // Create mode initializes default values.
        this.editMode = false;
        this.currentProject = null;
        txtAvancement.setText("0");
        btnDelete.setVisible(false);
        btnDelete.setManaged(false);
    }

    public void initForEdit(Project project) {
        // Edit mode pre-fills all fields from selected project.
        this.editMode = true;
        this.currentProject = project;
        txtTitle.setText(project.getTitleProj());
        txtDescription.setText(project.getDescriptionProj());
        txtBudget.setText(String.valueOf(project.getBudgetProj()));
        txtType.setText(project.getTypeProj());
        txtAvancement.setText(String.valueOf(project.getAvancementProj()));
        btnDelete.setVisible(true);
        btnDelete.setManaged(true);
    }

    @FXML
    private void onSave() {
        try {
            // Reuse existing object in edit mode, create a new one otherwise.
            Project p = (currentProject == null) ? new Project() : currentProject;
            p.setTitleProj(required(txtTitle.getText(), "Title is required"));
            p.setDescriptionProj(txtDescription.getText());
            p.setBudgetProj(parseNonNegative(txtBudget.getText(), "Budget"));
            p.setTypeProj(txtType.getText());
            p.setAvancementProj(parseRange(txtAvancement.getText(), 0, 100, "Avancement"));

            if (!editMode) {
                // Creation rule: project belongs to current client and starts as PENDING.
                p.setIdClient(SessionContext.getCurrentUserId());
                p.setStateProj(ProjectStatus.PENDING);
                projectService.add(p);
            } else {
                // Defensive default if DB returned null state unexpectedly.
                if (p.getStateProj() == null) {
                    p.setStateProj(ProjectStatus.PENDING);
                }
                projectService.update(p);
            }

            close();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onDelete() {
        try {
            // Delete is allowed only when editing an existing row.
            if (!editMode || currentProject == null || currentProject.getIdProj() <= 0) {
                throw new IllegalStateException("Delete is available only in edit mode");
            }
            projectService.delete(currentProject.getIdProj());
            close();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private double parseNonNegative(String value, String field) {
        try {
            double v = Double.parseDouble(required(value, field + " is required"));
            if (v < 0) {
                throw new IllegalArgumentException(field + " must be >= 0");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private double parseRange(String value, double min, double max, String field) {
        try {
            double v = Double.parseDouble(required(value, field + " is required"));
            if (v < min || v > max) {
                throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private void close() {
        Stage stage = (Stage) txtTitle.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Validation Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
