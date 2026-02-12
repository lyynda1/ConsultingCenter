/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: GUI controller: user interactions and screen flow
*/
package com.advisora.GUI.Project;
import com.advisora.Model.Decision;
import com.advisora.Services.DecisionService;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class DecisionListController implements Initializable {

    @FXML
    private TableView<Decision> decisionTable;
    @FXML
    private TableColumn<Decision, Integer> colId;
    @FXML
    private TableColumn<Decision, String> colStatus;
    @FXML
    private TableColumn<Decision, String> colDescription;
    @FXML
    private TableColumn<Decision, Integer> colProjectId;
    @FXML
    private TableColumn<Decision, Integer> colUserId;
    @FXML
    private TextField txtProjectFilter;
    @FXML
    private Button btnEdit;
    @FXML
    private Button btnDelete;

    private final DecisionService decisionService = new DecisionService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colId.setCellValueFactory(new PropertyValueFactory<>("idD"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("statutD"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("descriptionD"));
        colProjectId.setCellValueFactory(new PropertyValueFactory<>("idProj"));
        colUserId.setCellValueFactory(new PropertyValueFactory<>("idUser"));

        btnEdit.disableProperty().bind(Bindings.isNull(decisionTable.getSelectionModel().selectedItemProperty()));
        btnDelete.disableProperty().bind(Bindings.isNull(decisionTable.getSelectionModel().selectedItemProperty()));

        loadDecisions();
    }

    @FXML
    private void onAdd() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/decision/DecisionForm.fxml"));
            Parent root = loader.load();
            DecisionFormController controller = loader.getController();
            controller.initForCreate();
            openModal(root, "Add Decision");
            loadDecisions();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onEdit() {
        Decision selected = decisionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/decision/DecisionForm.fxml"));
            Parent root = loader.load();
            DecisionFormController controller = loader.getController();
            controller.initForEdit(selected);
            openModal(root, "Edit Decision");
            loadDecisions();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        Decision selected = decisionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            decisionService.delete(selected.getIdD());
            loadDecisions();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        loadDecisions();
    }

    @FXML
    private void onApplyFilter() {
        loadDecisions();
    }

    @FXML
    private void onOpenProjects() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/project/ProjectList.fxml"));
            Stage stage = (Stage) decisionTable.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Advisora - Projects");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void loadDecisions() {
        try {
            String filter = txtProjectFilter.getText();
            if (filter != null && !filter.isBlank()) {
                int projectId = Integer.parseInt(filter.trim());
                decisionTable.setItems(FXCollections.observableArrayList(decisionService.getByProject(projectId)));
            } else {
                decisionTable.setItems(FXCollections.observableArrayList(decisionService.getAll()));
            }
        } catch (NumberFormatException e) {
            showError("Project filter must be a number");
        } catch (Exception e) {
            showError(e.getMessage());
        }
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
