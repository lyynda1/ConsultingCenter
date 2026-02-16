/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: GUI controller: user interactions and screen flow
*/
package com.advisora.GUI.Project;

import com.advisora.Model.Decision;
import com.advisora.Services.DecisionService;
import com.advisora.Services.SessionContext;
import com.advisora.enums.DecisionStatus;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.time.LocalDateTime;

public class DecisionFormController {
    @FXML
    private ChoiceBox<DecisionStatus> choiceStatus;
    @FXML
    private TextArea txtDescription;
    @FXML
    private TextField txtProjectId;

    private final DecisionService decisionService = new DecisionService();
    private Decision currentDecision;
    private boolean editMode;

    @FXML
    private void initialize() {
        choiceStatus.setItems(FXCollections.observableArrayList(DecisionStatus.values()));
        choiceStatus.setValue(DecisionStatus.PENDING);
    }

    public void initForCreate() {
        this.editMode = false;
        this.currentDecision = null;
    }

    public void initWithProjectId(int idProj) {
        initForCreate();
        txtProjectId.setText(String.valueOf(idProj));
    }

    public void initForEdit(Decision decision) {
        this.editMode = true;
        this.currentDecision = decision;
        choiceStatus.setValue(decision.getStatutD());
        txtDescription.setText(decision.getDescriptionD());
        txtProjectId.setText(String.valueOf(decision.getIdProj()));
    }

    @FXML
    private void onSave() {
        try {
            Decision d = (currentDecision == null) ? new Decision() : currentDecision;
            d.setStatutD(choiceStatus.getValue());
            d.setDescriptionD(required(txtDescription.getText(), "Description is required"));
            d.setIdProj(parsePositiveInt(txtProjectId.getText(), "Project id"));

            // Decision date is always system date/time (no manual input in form).
            d.setDateDecision(LocalDateTime.now());
            d.setIdUser(SessionContext.getCurrentUserId());

            if (editMode) {
                decisionService.update(d);
            } else {
                decisionService.add(d);
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

    private int parsePositiveInt(String value, String field) {
        try {
            int v = Integer.parseInt(required(value, field + " is required"));
            if (v <= 0) {
                throw new IllegalArgumentException(field + " must be > 0");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private void close() {
        Stage stage = (Stage) txtProjectId.getScene().getWindow();
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
        choiceStatus.setValue(DecisionStatus.PENDING);
        txtDescription.clear();
        txtProjectId.clear();
    }
}
