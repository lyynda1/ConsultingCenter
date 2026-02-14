package com.advisora.GUI.Strategie;

import com.advisora.Model.Strategie;          // ✅ change if your package is different
import com.advisora.Services.ServiceStrategie; // ✅ change if your service name/package is different

import com.advisora.enums.StrategyStatut;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.event.ActionEvent;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public class StrategieListController {

    // ---------- FXML ----------
    @FXML private ListView<Strategie> strategieList;
    @FXML private TextField txtSearch;

    @FXML private Label lblTotalStrategies;
    @FXML private Label lblPending;
    @FXML private Label lblSuccess;

    // ---------- Data ----------
    private final ServiceStrategie strategieService = new ServiceStrategie();
    private final ObservableList<Strategie> allObs = FXCollections.observableArrayList();
    private final ObservableList<Strategie> viewObs = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // 1) bind data to the list
        strategieList.setItems(viewObs);

        // 2) ✅ PUT THE CELL FACTORY HERE
        strategieList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Strategie s, boolean empty) {
                super.updateItem(s, empty);

                if (empty || s == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label title = new Label(safe(s.getNomStrategie()));
                title.getStyleClass().add("card-title");

                Label statut = new Label(s.getStatut() == null ? "" : s.getStatut().name());
                statut.getStyleClass().add("badge");

                Region spacer = new Region();
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

                HBox head = new HBox(10, title, spacer, statut);

                Label date = new Label("Créée le: " +
                        (s.getCreatedAt() == null ? "-" : s.getCreatedAt().toLocalDate().toString()));
                date.getStyleClass().add("card-sub");
                Label projet = new Label("Projet: " + (s.getProjet() == null ? "-" : s.getProjet().getTitleProj()));
                projet.getStyleClass().add("card-sub");

                Button btnEdit = new Button("Modifier");
                btnEdit.getStyleClass().add("btn-ghost");

                Button btnDelete = new Button("Supprimer");
                btnDelete.getStyleClass().add("btn-danger");

                HBox actions = new HBox(8, btnEdit, btnDelete);

                VBox card = new VBox(10, head, projet, date, actions);
                card.getStyleClass().add("card");

                setText(null);
                setGraphic(card);
            }
        });

        // 3) load data after cell factory is set
        refresh();

        // optional search listener
        txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));
    }


    // =========================================================
    // Handlers referenced by FXML
    // =========================================================

    @FXML private StackPane overlay;
    @FXML private VBox modalBox;

    private Parent addDialogContent;

    @FXML
    private void nouvelleStrategie(ActionEvent e) {
        openAddDialog();
    }


    private void openAddDialog() {
        try {
            if (addDialogContent == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/strategie/AddStrategie.fxml"));
                addDialogContent = loader.load();
                AddStrategieDialogController c = loader.getController();
                enableDrag(c.getDragHandle(), modalBox);

                // give dialog controller a callback to close + refresh
                c.setOnClose(this::closeDialog);
                c.setOnSaved(() -> {
                    closeDialog();
                    refresh(); // reload list after insert
                });
            }

            modalBox.getChildren().setAll(addDialogContent);
            overlay.setManaged(true);
            overlay.setVisible(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir le formulaire: " + ex.getMessage()).showAndWait();
        }
    }
    private double dragOffsetX;
    private double dragOffsetY;

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

    private void closeDialog() {
        overlay.setVisible(false);
        overlay.setManaged(false);
    }


    @FXML
    private void onSearch(KeyEvent e) {
        // If you keep onKeyReleased in FXML, this method must exist.
        // We still filter from the text field value.
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortNomAsc(ActionEvent e) {
        sortView(Comparator.comparing(s -> safe(getNom(s)).toLowerCase(Locale.ROOT)));
    }

    @FXML
    private void onSortNomDesc(ActionEvent e) {
        sortView(Comparator.comparing((Strategie s) -> safe(getNom(s)).toLowerCase(Locale.ROOT)).reversed());
    }

    @FXML
    private void onSortDateDesc(ActionEvent e) {
        sortView(Comparator.comparing(this::getDateCreationSafe).reversed());
    }

    @FXML
    private void onSortDateAsc(ActionEvent e) {
        sortView(Comparator.comparing(this::getDateCreationSafe));
    }

    // =========================================================
    // CRUD actions
    // =========================================================

    private void onEdit(Strategie s) {
        // TODO open edit screen with s
        System.out.println("Edit strategie id=" + getIdSafe(s));
    }

    private void onDelete(Strategie s) {
        if (s == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cette stratégie ?");
        confirm.setContentText("Cette action est irréversible.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            strategieService.supprimer(s);
            refresh();
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Erreur");
            err.setHeaderText("Suppression échouée");
            err.setContentText(ex.getMessage());
            err.showAndWait();
        }
    }

    // =========================================================
    // Data refresh + filter + stats
    // =========================================================

    private void refresh() {
        List<Strategie> list = strategieService.afficher(); // ✅ adapt if your method name differs
        allObs.setAll(list);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
        updateStats(allObs);
    }

    private void applyFilter(String q) {
        String s = (q == null) ? "" : q.trim().toLowerCase(Locale.ROOT);

        if (s.isBlank()) {
            viewObs.setAll(allObs);
            return;
        }

        viewObs.setAll(allObs.stream().filter(st ->
                safe(getNom(st)).toLowerCase(Locale.ROOT).contains(s)
                        || safe(getProjetNom(st)).toLowerCase(Locale.ROOT).contains(s)
                        || safe(getStatut(st)).toLowerCase(Locale.ROOT).contains(s)
                        || safe(getDateCreationText(st)).toLowerCase(Locale.ROOT).contains(s)
        ).toList());
    }


    private void sortView(Comparator<Strategie> comparator) {
        FXCollections.sort(viewObs, comparator);
    }

    private void updateStats(List<Strategie> list) {
        int total = list == null ? 0 : list.size();

        long enCours = list == null ? 0 : list.stream()
                .filter(s -> s.getStatut() == StrategyStatut.En_cours)
                .count();

        long acceptee = list == null ? 0 : list.stream()
                .filter(s -> s.getStatut() == StrategyStatut.Acceptée)
                .count();

        String rate = total == 0 ? "0%" : Math.round((acceptee * 100.0) / total) + "%";

        if (lblTotalStrategies != null) lblTotalStrategies.setText(String.valueOf(total));
        if (lblPending != null) lblPending.setText(String.valueOf(enCours));
        if (lblSuccess != null) lblSuccess.setText(rate);
    }


    // =========================================================
    // ADAPT HERE: map to your real Strategie model fields
    // =========================================================

    // ✅ correct mapping for your model/service
    private String getNom(Strategie s) {
        return s.getNomStrategie();   // not getNom()
    }

    private String getStatut(Strategie s) {
        return s.getStatut() == null ? "" : s.getStatut().name();
    }

    private String getProjetNom(Strategie s) {
        // If you don't have project relation yet:
        return "-";
    }

    private LocalDateTime getCreatedAtSafe(Strategie s) {
        return s.getCreatedAt();      // matches CreatedAtS
    }

    private String getDateCreationText(Strategie s) {
        LocalDateTime d = getCreatedAtSafe(s);
        return d == null ? "-" : d.toLocalDate().toString();
    }


    private LocalDateTime getDateCreationSafe(Strategie s) {
        // example: return s.getDateCreation();
        try {
            return s.getCreatedAt(); // ✅ change if your getter differs
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isPending(Strategie s) {
        // customize to your statuses, e.g. "EN_ATTENTE"
        String st = safe(getStatut(s)).toLowerCase(Locale.ROOT);
        return st.contains("attente") || st.contains("pending");
    }

    private boolean isSuccess(Strategie s) {
        // customize to your statuses, e.g. "VALIDEE"
        String st = safe(getStatut(s)).toLowerCase(Locale.ROOT);
        return st.contains("val") || st.contains("success") || st.contains("ok");
    }

    private int getIdSafe(Strategie s) {
        try {
            return s.getId();   // or s.getIdStrategie() if your getter is named that way
        } catch (Exception ex) {
            return 0;
        }
    }


    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
