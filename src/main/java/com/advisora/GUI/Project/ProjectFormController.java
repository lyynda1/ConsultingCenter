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
import javafx.scene.control.ButtonType;
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
    private Button btnDelete;

    private final ProjectService projectService = new ProjectService();
    private Project currentProject;
    private boolean editMode;

    public void initForCreate() {
        this.editMode = false;
        this.currentProject = null;
        btnDelete.setVisible(false);
        btnDelete.setManaged(false);
    }

    public void initForEdit(Project project) {
        this.editMode = true;
        this.currentProject = project;
        txtTitle.setText(project.getTitleProj());
        txtDescription.setText(project.getDescriptionProj());
        txtBudget.setText(String.valueOf(project.getBudgetProj()));
        txtType.setText(project.getTypeProj());
        btnDelete.setVisible(true);
        btnDelete.setManaged(true);
    }

    @FXML
    private void onSave() {
        try {
            Project p = (currentProject == null) ? new Project() : currentProject;
            p.setTitleProj(required(txtTitle.getText(), "Le titre est requis"));
            p.setDescriptionProj(required(txtDescription.getText(), "Veuillez fournir une description"));
            p.setBudgetProj(parseNonNegative(txtBudget.getText(), "Le budget"));
            p.setTypeProj(required(txtType.getText(), "Le type de projet est requis"));
            p.setAvancementProj(0);

            if (!editMode) {
                p.setIdClient(SessionContext.getCurrentUserId());
                p.setStateProj(ProjectStatus.PENDING);
                projectService.add(p);
            } else {
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
        clearForm();
        showInfo("Form cleared. Use Save to apply changes.");
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private double parseNonNegative(String value, String field) {
        try {
            double v = Double.parseDouble(required(value, field + " est requis"));
            if (v < 0) {
                throw new IllegalArgumentException(field + " doit etre >= 0");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " doit etre un nombre");
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

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText("Information");
        alert.showAndWait();
    }

    private void clearForm() {
        txtTitle.clear();
        txtDescription.clear();
        txtBudget.clear();
        txtType.clear();
    }
}
