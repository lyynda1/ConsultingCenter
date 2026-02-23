package com.advisora.GUI.Strategie;

import com.advisora.Model.projet.Project;
import com.advisora.Model.strategie.SimilarityResult;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.projet.ProjectService;
import com.advisora.Services.strategie.ServiceStrategie;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.TypeStrategie;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class AddStrategieDialogController {
    @FXML private Label titleLabel;
    @FXML private TextField nomField;
    @FXML private ComboBox<Project> projetCombo;
    @FXML private ComboBox<StrategyStatut> statutCombo;
    @FXML private ComboBox<TypeStrategie> typeCombo;
    @FXML private TextField budgetTotalField;
    @FXML private TextField gainEstimeField;
    @FXML private TextField versionField;
    @FXML private HBox dragHandle;
    @FXML private Button saveBtn;

    private final ServiceStrategie serviceStrategie = new ServiceStrategie();
    private final ProjectService projectService = new ProjectService();
    private Runnable onSaved = () -> {};
    private Runnable onClose = () -> {};
    private Strategie editingStrategie;

    @FXML
    public void initialize() {
        statutCombo.setItems(FXCollections.observableArrayList(StrategyStatut.values()));
        statutCombo.setValue(StrategyStatut.EN_COURS);
        typeCombo.setItems(FXCollections.observableArrayList(TypeStrategie.values()));
        typeCombo.setValue(TypeStrategie.NULL);
        budgetTotalField.setText("0");
        gainEstimeField.setText("0");

        versionField.setText("1");
        projetCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Project p) {
               return p == null ? "" : "#" + p.getIdProj() + " - " + p.getTitleProj();
            }

            @Override
            public Project fromString(String string) {
                return null;
            }
        });

        List<Project> projects = serviceStrategie.listProjets();
        projetCombo.setItems(FXCollections.observableArrayList(projects));
    }

    public Node getDragHandle() {
        return dragHandle;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void initForCreate(Runnable onSaved) {
        setOnSaved(onSaved);
        setEditingStrategie(null);
        resetForm();
    }

    public void initForEdit(Strategie strategie, Runnable onSaved) {
        setOnSaved(onSaved);
        setEditingStrategie(strategie);
    }

    public void setEditingStrategie(Strategie strategie) {
        this.editingStrategie = strategie;

        if (strategie == null) {
            resetForm();
            return;
        }

        nomField.setText(strategie.getNomStrategie());
        versionField.setText(String.valueOf(strategie.getVersion()));
        statutCombo.setValue(strategie.getStatut());
        typeCombo.setValue(strategie.getTypeStrategie());
        selectProjectById(strategie.getProjet() == null ? 0 : strategie.getProjet().getIdProj());
        budgetTotalField.setText(String.valueOf(strategie.getBudgetTotal()));
        gainEstimeField.setText(String.valueOf(strategie.getGainEstime()));
        if (titleLabel != null) {
            titleLabel.setText("Modifier Stratégie");
        }
        if (saveBtn != null) {
            saveBtn.setText("Enregistrer");
        }
    }

    public void resetForm() {
        editingStrategie = null;
        nomField.clear();
        versionField.setText("1");
        statutCombo.setValue(StrategyStatut.EN_COURS);
        projetCombo.getSelectionModel().clearSelection();
        budgetTotalField.setText("0");
        gainEstimeField.setText("0");
        if (titleLabel != null) {
            titleLabel.setText("Nouvelle Stratégie");
        }
        if (saveBtn != null) {
            saveBtn.setText("Ajouter");
        }
    }

    @FXML
    private void save() {
        try {
            StrategyStatut statut = statutCombo.getValue();
            if (statut == null) {
                statut = StrategyStatut.EN_COURS;
            }


            int version = parsePositiveInt(versionField.getText(), "Version");
            Project selectedProject = projetCombo.getValue();
            if (selectedProject == null || selectedProject.getIdProj() <= 0) {
                throw new IllegalArgumentException("Projet obligatoire.");
            }

            Strategie s = editingStrategie == null ? new Strategie() : editingStrategie;
            s.setNomStrategie(required(nomField.getText(), "Nom strategie obligatoire."));
            s.setVersion(version);
            s.setStatut(statut);
            s.setProjet(selectedProject);
            s.setIdUser(SessionContext.getCurrentUserId());
            s.setTypeStrategie(typeCombo.getValue());
            s.setBudgetTotal(parsePositiveDouble(budgetTotalField.getText(), "Budget total invalide."));
            s.setGainEstime(parsePositiveDouble(gainEstimeField.getText(), "Gain estime invalide."));
            if (s.getCreatedAt() == null) {
                s.setCreatedAt(LocalDateTime.now());
            }

            if (editingStrategie == null) {
                serviceStrategie.ajouter(s);
            } else {
                serviceStrategie.modifier(s);

            }

            onSaved.run();
            close();
        } catch (Exception e) {
            Alert a = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            a.setHeaderText("Strategie");
            a.showAndWait();
        }
    }

    @FXML
    private void close() {
        if (onClose != null) {
            onClose.run();
        }
        if (nomField != null && nomField.getScene() != null && nomField.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }

    private void selectProjectById(int projectId) {
        if (projectId <= 0) {
            projetCombo.getSelectionModel().clearSelection();
            return;
        }
        ObservableList<Project> projects = projetCombo.getItems();
        for (Project p : projects) {
            if (p.getIdProj() == projectId) {

                projetCombo.setValue(p);
                break;
            }
        }
    }

    private String required(String value, String msg) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(msg);
        }

        return value.trim();

    }

    private int parsePositiveInt(String value, String field) {
        try {
            int v = Integer.parseInt(required(value, field + " obligatoire."));
            if (v <= 0) {
                throw new IllegalArgumentException(field + " doit etre > 0.");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " invalide.");
        }
    }
}
