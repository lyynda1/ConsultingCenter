package com.advisora.GUI.Strategie;

import com.advisora.Model.Project;
import com.advisora.Model.Strategie;
import com.advisora.Services.ProjectService;
import com.advisora.Services.ServiceStrategie;
import com.advisora.Util.DB;
import com.advisora.enums.StrategyStatut;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AddStrategieDialogController {

    @FXML private TextField nomField;
    @FXML private TextField versionField;
    @FXML private TextArea newsField;         // if you have it in FXML (optional)
    @FXML private ComboBox<StrategyStatut> statutCombo;
    @FXML private HBox dragHandle;
    @FXML private Label dialogTitle;          // add in FXML (optional)
    @FXML private Button saveBtn;

    public List<Project> afficher() {
        List<Project> list = new ArrayList<>();
        String sql = "SELECT idProj, titleProj FROM projects";

        try (Connection conn = DB.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                Project p = new Project();
                p.setIdProj(rs.getInt("idProj"));
                p.setTitleProj(rs.getString("titleProj"));
                list.add(p);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error fetching projects: " + e.getMessage(), e);
        }
        return list;
    }
    @FXML private ComboBox<Project> projetCombo;

    private final ProjectService projectService = new ProjectService();// add in FXML (optional)

    private final ServiceStrategie service = new ServiceStrategie();

    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};

    private Strategie editing; // ✅ if not null => edit mode

    @FXML

    public void initialize() {
        statutCombo.getItems().setAll(StrategyStatut.values());
        statutCombo.getSelectionModel().selectFirst();

        // ✅ show project names in combo
        projetCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Project p) {
                return (p == null) ? "" : p.getTitleProj();
            }
            @Override public Project fromString(String s) { return null; }
        });

        // ✅ load from DB
        projetCombo.setItems(FXCollections.observableArrayList(projectService.getAll()));
    }



    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable r) { this.onClose = r; }
    public void setOnSaved(Runnable r) { this.onSaved = r; }

    /** ✅ Call this before showing dialog when editing */
    public void setEditingStrategie(Strategie s) {
        this.editing = s;

        if (s == null) {
            if (dialogTitle != null) dialogTitle.setText("Nouvelle Stratégie");
            if (saveBtn != null) saveBtn.setText("Ajouter");

            if (nomField != null) nomField.clear();
            if (versionField != null) versionField.clear(); // or setText("1.0")
            if (newsField != null) newsField.clear();
            if (statutCombo != null) statutCombo.getSelectionModel().selectFirst();

            return; // IMPORTANT
        }

        // ✅ EDIT MODE
        if (dialogTitle != null) dialogTitle.setText("Modifier Stratégie");
        if (saveBtn != null) saveBtn.setText("Enregistrer");

        nomField.setText(s.getNomStrategie());
        versionField.setText(String.valueOf(s.getVersion()));
        if (newsField != null) newsField.setText(s.getNews() == null ? "" : s.getNews());
        if (statutCombo != null) statutCombo.setValue(s.getStatut());
    }

    @FXML
    private void close() {
        onClose.run();
    }

    @FXML
    private void save() {
        String nom = nomField.getText() == null ? "" : nomField.getText().trim();
        String verTxt = versionField.getText() == null ? "" : versionField.getText().trim();

        if (nom.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Nom obligatoire").showAndWait();
            return;
        }

        double version;
        try {
            version = Double.parseDouble(verTxt);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.WARNING, "Version invalide").showAndWait();
            return;
        }

        StrategyStatut statut = statutCombo.getValue();
        String news = (newsField == null) ? null : newsField.getText();
        Project selectedProject = projetCombo.getValue(); // can be null

        try {
            if (editing == null) {
                // ADD
                Strategie s = new Strategie(nom, version, statut, LocalDateTime.now(), null, news, selectedProject);
                service.ajouter(s);
            } else {
                // ✅ CONFIRM EDIT
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirmation");
                confirm.setHeaderText("Confirmer la modification ?");
                confirm.setContentText("Stratégie ID: " + editing.getId() + "\nNom: " + editing.getNomStrategie());

                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

                // UPDATE
                editing.setNomStrategie(nom);
                editing.setVersion(version);
                editing.setStatut(statut);
                editing.setProjet(selectedProject);
                editing.setNews(news);

                service.modifier(editing);
            }

            onSaved.run();

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Erreur: " + ex.getMessage()).showAndWait();
        }
        resetForm();
    }

    public void resetForm() {
        editing = null;
        nomField.clear();
        versionField.clear(); // or setText("1.0") if you want default
        if (newsField != null) newsField.clear();
        statutCombo.getSelectionModel().selectFirst();


        if (dialogTitle != null) dialogTitle.setText("Nouvelle Stratégie");
        if (saveBtn != null) saveBtn.setText("Ajouter");
    }


}
