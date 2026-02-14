package com.advisora.GUI.Strategie;

import com.advisora.GUI.Objective.*;

import com.advisora.Model.Strategie;
import com.advisora.Services.ServiceStrategie;
import com.advisora.Model.Objective;
import com.advisora.Services.ServiceObjective;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;

import java.util.Map;
import java.util.stream.Collectors;

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
    private final ServiceObjective objectiveService = new ServiceObjective();
    private Map<Integer, List<Objective>> objectivesByStrategie = Map.of();

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
                Label projet = new Label("Projet associé: " + (s.getProjet() == null ? "-" : s.getProjet().getTitleProj()));
                projet.getStyleClass().add("card-sub");

                Button btnEdit = new Button("Modifier");
                btnEdit.getStyleClass().add("btn-ghost");
                btnEdit.setOnAction(e -> onEdit(s));
                Button btnDelete = new Button("Supprimer");
                btnDelete.getStyleClass().add("btn-danger");
                btnDelete.setOnAction(e -> onDelete(s));

                Button btnObjectives = new Button("Attribuer des objectifs");
                btnObjectives.getStyleClass().add("btn-ghost");
                btnObjectives.setOnAction(e -> onObjectives(s));


                // ✅ Objective chips row
                FlowPane objChips = new FlowPane();
                objChips.getStyleClass().add("obj-chips");
                objChips.setHgap(8);
                objChips.setVgap(8);

                List<Objective> objs = objectivesByStrategie.getOrDefault(s.getId(), List.of());

                if (objs.isEmpty()) {
                    Label none = new Label("Aucun objectif");
                    none.getStyleClass().add("obj-empty");
                    objChips.getChildren().add(none);
                } else {
                    for (Objective o : objs) {
                        Button chip = new Button(o.getNomObjective()); // or a short name
                        chip.getStyleClass().add("obj-chip");

                        // ✅ click chip -> edit this objective
                        chip.setOnAction(ev -> openObjectiveDialog(o, s));

                        objChips.getChildren().add(chip);
                    }
                }

                HBox actions = new HBox(8, btnEdit, btnDelete, btnObjectives);

                VBox card = new VBox(10, head, projet, date, objChips, actions);
                card.getStyleClass().add("card");

                setText(null);
                setGraphic(card);
            }
        });

        // 3) load data after cell factory is set
        refresh();

        // optional search listener
        txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));

        StackPane.setAlignment(modalBox, Pos.CENTER);
        modalBox.setMaxWidth(Region.USE_PREF_SIZE);
        modalBox.setMaxHeight(Region.USE_PREF_SIZE);



    }

    private void openObjectiveDialog(Objective obj, Strategie s) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/objectif/ObjectiveInfoDialog.fxml")
            );
            Parent content = loader.load();

            com.advisora.GUI.Objective.ObjectiveInfoController controller = loader.getController();
            controller.setObjective(obj);
            controller.setOnEditRequested(o -> openEditObjectiveDialog(o, s));

            controller.setOnClose(this::closeDialog);
            controller.setOnRefresh(this::refresh);

            showDialog(content);


            enableDrag(controller.getDragHandle(), modalBox);
            controller.setOnEditRequested(o -> openEditObjectiveDialog(o, s));


        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    private void openEditObjectiveDialog(Objective obj, Strategie s) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/objectif/AddObjectif.fxml"));
            Parent content = loader.load();

            AddObjectifController c = loader.getController();
            enableDrag(c.getDragHandle(), modalBox);

            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });

            c.setStrategie(s);
            c.setEditingObjective(obj);

            modalBox.getChildren().setAll(content);
            overlay.setManaged(true);
            overlay.setVisible(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir modification objectif: " + ex.getMessage()).showAndWait();
        }
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



    private void showDialog(Parent content) {
        // reset drag position
        modalBox.setTranslateX(0);
        modalBox.setTranslateY(0);

        // prevent the loaded root from expanding
        if (content instanceof Region r) {
            r.setMaxWidth(Region.USE_PREF_SIZE);
            r.setMaxHeight(Region.USE_PREF_SIZE);
        }

        modalBox.getChildren().setAll(content);
        overlay.setManaged(true);
        overlay.setVisible(true);
    }

    private void openAddDialog() {
        try {

            if (addDialogContent == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/strategie/AddStrategie.fxml"));
                addDialogContent = loader.load();
                AddStrategieDialogController c = loader.getController();
                c.setEditingStrategie(null);
                enableDrag(c.getDragHandle(), modalBox);

                // give dialog controller a callback to close + refresh
                c.setOnClose(this::closeDialog);
                c.setOnSaved(() -> {
                    closeDialog();
                    refresh(); // reload list after insert
                });
                c.resetForm();
            }

            showDialog(addDialogContent);



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
        modalBox.getChildren().clear();     // ✅ IMPORTANT
        modalBox.setTranslateX(0);          // optional (if you drag)
        modalBox.setTranslateY(0);
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
        if (s == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/strategie/AddStrategie.fxml"));
            Parent content = loader.load();

            AddStrategieDialogController c = loader.getController();

            // drag handle
            enableDrag(c.getDragHandle(), modalBox);

            // callbacks
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });

           
            c.setEditingStrategie(s);

            modalBox.getChildren().setAll(content);
            overlay.setManaged(true);
            overlay.setVisible(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir modification: " + ex.getMessage()).showAndWait();
        }
    }

    private void onDelete(Strategie s) {
        if (s == null) return;

        int id = s.getId();
        if (id <= 0) {
            new Alert(Alert.AlertType.ERROR, "Impossible de supprimer: ID invalide.").showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cette stratégie ?");
        confirm.setContentText("ID: " + id + "\nCette action est irréversible.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            strategieService.supprimer(s);  // uses id inside
            refresh();
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Suppression échouée: " + ex.getMessage()).showAndWait();
        }
    }

    private void onObjectives(Strategie s) {
        if (s == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/objectif/AddObjectif.fxml"));
            Parent content = loader.load();

            AddObjectifController c = loader.getController();

            // drag handle
            enableDrag(c.getDragHandle(), modalBox);

            // callbacks
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });

            c.setStrategie(s);
            c.resetForm();
            modalBox.getChildren().setAll(content);
            overlay.setManaged(true);
            overlay.setVisible(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir objectifs: " + ex.getMessage()).showAndWait();
        }

    }

    // =========================================================
    // Data refresh + filter + stats
    // =========================================================

    private void refresh() {
        List<Strategie> list = strategieService.afficher();
        allObs.setAll(list);

        // ✅ Load objectives once, group by strategieId
        List<Objective> allObjectives = objectiveService.afficher();
        objectivesByStrategie = allObjectives.stream()
                .collect(Collectors.groupingBy(Objective::getStrategieId));

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
