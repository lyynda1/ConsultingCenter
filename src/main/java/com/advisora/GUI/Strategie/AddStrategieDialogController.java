package com.advisora.GUI.Strategie;

import com.advisora.Model.Strategie;
import com.advisora.Services.ServiceStrategie;
import com.advisora.enums.StrategyStatut;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.time.LocalDateTime;

public class AddStrategieDialogController {

    @FXML private TextField nomField;
    @FXML private TextField versionField;
    @FXML private TextArea newsField;         // if you have it in FXML (optional)
    @FXML private ComboBox<StrategyStatut> statutCombo;
    @FXML private HBox dragHandle;
    @FXML private Label dialogTitle;          // add in FXML (optional)
    @FXML private Button saveBtn;             // add in FXML (optional)

    private final ServiceStrategie service = new ServiceStrategie();

    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};

    private Strategie editing; // ✅ if not null => edit mode

    @FXML
    public void initialize() {
        statutCombo.getItems().setAll(StrategyStatut.values());
        statutCombo.getSelectionModel().selectFirst();
    }

    public Node getDragHandle() { return dragHandle; }
    public void setOnClose(Runnable r) { this.onClose = r; }
    public void setOnSaved(Runnable r) { this.onSaved = r; }

    /** ✅ Call this before showing dialog when editing */
    public void setEditingStrategie(Strategie s) {
        this.editing = s;

        if (dialogTitle != null) dialogTitle.setText("Modifier Stratégie");
        if (saveBtn != null) saveBtn.setText("Enregistrer");

        // prefill fields
        nomField.setText(s.getNomStrategie());
        versionField.setText(String.valueOf(s.getVersion()));
        if (newsField != null) newsField.setText(s.getNews() == null ? "" : s.getNews());
        statutCombo.setValue(s.getStatut());
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

        try {
            if (editing == null) {
                // ADD
                Strategie s = new Strategie(nom, version, statut, LocalDateTime.now(), null, news, null);
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
