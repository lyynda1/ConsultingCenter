package com.advisora.GUI.Strategie;

import com.advisora.GUI.Objective.AddObjectifController;
import com.advisora.GUI.Objective.ObjectiveInfoController;
import com.advisora.Model.Objective;
import com.advisora.Model.Strategie;
import com.advisora.Services.ServiceObjective;
import com.advisora.Services.ServiceStrategie;
import com.advisora.Services.SessionContext;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class StrategieListController {
    @FXML private ListView<Strategie> strategieList;
    @FXML private TextField txtSearch;
    @FXML private Label lblTotalStrategies;
    @FXML private Label lblPending;
    @FXML private Label lblSuccess;
    @FXML private StackPane overlay;
    @FXML private VBox modalBox;

    private final ServiceStrategie strategieService = new ServiceStrategie();
    private final ServiceObjective objectiveService = new ServiceObjective();
    private final ObservableList<Strategie> allObs = FXCollections.observableArrayList();
    private final ObservableList<Strategie> viewObs = FXCollections.observableArrayList();
    private Map<Integer, List<Objective>> objectivesByStrategie = Map.of();
    private Comparator<Strategie> comparator = Comparator.comparing(this::getCreatedAtSafe, this::compareDateDesc);
    private double dragOffsetX;
    private double dragOffsetY;

    @FXML

    public void initialize() {

        strategieList.setItems(viewObs);

        // ✅ allow dynamic cell height (otherwise labels can be clipped)
        strategieList.setFixedCellSize(-1);

        strategieList.setCellFactory(lv -> new ListCell<>() {

            {
                // ✅ show ONLY graphic (your VBox card)
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

                // ✅ let cell width follow list width (helps wrapping)
                prefWidthProperty().bind(lv.widthProperty().subtract(20)); // small margin
            }

            @Override
            protected void updateItem(Strategie s, boolean empty) {
                super.updateItem(s, empty);

                if (empty || s == null) {
                    setGraphic(null);
                } else {
                    VBox card = buildCard(s);

                    // ✅ VERY IMPORTANT: give the card a width to wrap into
                    card.maxWidthProperty().bind(prefWidthProperty());
                    card.setFillWidth(true);

                    setGraphic(card);
                }
            }
        });

        txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));

        try {
            refresh();
        } catch (Exception ex) {
            showError("Chargement strategies impossible: " + ex.getMessage());
            viewObs.clear();
            lblTotalStrategies.setText("0");
            lblPending.setText("0");
            lblSuccess.setText("0%");
        }
    }


    @FXML
    private void nouvelleStrategie(ActionEvent e) {
        if (!canManage()) {
            showError("Acces refuse: GERANT ou ADMIN requis.");
            return;
        }
        openAddDialog();
    }

    @FXML
    private void onSearch(KeyEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortNomAsc(ActionEvent e) {
        comparator = Comparator.comparing(s -> safe(s.getNomStrategie()).toLowerCase(Locale.ROOT));
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortNomDesc(ActionEvent e) {
        comparator = Comparator.comparing((Strategie s) -> safe(s.getNomStrategie()).toLowerCase(Locale.ROOT)).reversed();
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortDateDesc(ActionEvent e) {
        comparator = Comparator.comparing(this::getCreatedAtSafe, this::compareDateDesc);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortDateAsc(ActionEvent e) {
        comparator = Comparator.comparing(this::getCreatedAtSafe, this::compareDateAsc);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    private VBox buildCard(Strategie s) {

        // ===== HEADER =====
        Label title = new Label(safe(s.getNomStrategie()));
        title.getStyleClass().add("card-title");

        // Status + icon
        String statusText = (s.getStatut() == null) ? "" : s.getStatut().toDb();
        String statusIcon = switch (s.getStatut()) {
            case ACCEPTEE -> "✅ ";
            case REFUSEE  -> "⛔ ";
            case EN_COURS -> "⏳ ";
            default -> "";
        };

        Label statut = new Label(statusIcon + statusText);
        statut.getStyleClass().addAll("badge", "status-badge");

        // status color class
        if (s.getStatut() != null) {
            switch (s.getStatut()) {
                case ACCEPTEE -> statut.getStyleClass().add("status-accepted");
                case REFUSEE  -> statut.getStyleClass().add("status-refused");
                case EN_COURS -> statut.getStyleClass().add("status-pending");
            }
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox head = new HBox(10, title, spacer, statut);
        head.getStyleClass().add("card-head");


        // ===== LEFT META (Projet / Date / Type) =====
        Label projet = new Label("Projet associé : " +
                (s.getProjet() == null ? "-" : safe(s.getProjet().getTitleProj())));
        projet.getStyleClass().add("card-meta");

        Label date = new Label("Date de création : " +
                (s.getCreatedAt() == null ? "-" : s.getCreatedAt().toLocalDate()));
        date.getStyleClass().add("card-meta");

        Label type = new Label("Type : " +
                (s.getTypeStrategie() == null ? "-" : safe(s.getTypeStrategie().name())));
        type.getStyleClass().add("card-meta");

        VBox metaLeft = new VBox(6, projet, date, type);
        metaLeft.getStyleClass().add("card-meta-col");


        // ===== RIGHT METRICS (Budget / Gain / ROI) =====
        String budgetText = (s.getBudgetTotal() == 0) ? "-" : String.format("%,.0f DT", s.getBudgetTotal());
        String gainText   = (s.getGainEstime() == 0) ? "-" : String.format("%,.0f DT", s.getGainEstime());

        Label budget = new Label("Budget : " + budgetText);
        budget.getStyleClass().add("metric-line");

        Label gain = new Label("Gain estimé : " + gainText);
        gain.getStyleClass().add("metric-line");

        Label roi = new Label("ROI : -");
        roi.getStyleClass().add("roi-value");

        if (s.getGainEstime() != 0 && s.getBudgetTotal() != 0 && s.getBudgetTotal() != 0) {
            double roiValue = strategieService.CalculROI(s.getGainEstime(), s.getBudgetTotal()) * 100.0;
            roi.setText(String.format("ROI : %+,.0f%%", roiValue));
        }

        VBox metricsRight = new VBox(6, budget, gain, roi);
        metricsRight.getStyleClass().add("metrics-col");


        // ===== TOP GRID (left + right) =====
        HBox topRow = new HBox(22, metaLeft, metricsRight);
        topRow.getStyleClass().add("card-top-row");
        HBox.setHgrow(metaLeft, Priority.ALWAYS);


        // ===== OBJECTIVES CHIPS =====
        FlowPane objChips = new FlowPane();
        objChips.getStyleClass().add("obj-chips");
        objChips.setHgap(8);
        objChips.setVgap(8);

        List<Objective> objectives = objectivesByStrategie.getOrDefault(s.getId(), List.of());

        if (objectives.isEmpty()) {
            Label none = new Label("Aucun objectif");
            none.getStyleClass().add("obj-empty");
            objChips.getChildren().add(none);
        } else {
            for (Objective o : objectives) {
                Button chip = new Button(safe(o.getNomObjective()));
                chip.getStyleClass().add("obj-chip");
                chip.setOnAction(ev -> openObjectiveInfoDialog(o, s));
                objChips.getChildren().add(chip);
            }
        }


        // ===== JUSTIFICATION =====
        Label justificationLabel = new Label();
        justificationLabel.getStyleClass().add("justification");
        justificationLabel.setWrapText(true);
        justificationLabel.setMaxWidth(Double.MAX_VALUE);

        String j = s.getJustification();
        boolean hasJustif = j != null && !j.trim().isEmpty();
        justificationLabel.setText(hasJustif ? "Justification : " + j.trim() : "");
        justificationLabel.setVisible(hasJustif);
        justificationLabel.setManaged(hasJustif);


        // ===== ACTIONS =====
        HBox actions = new HBox(10);
        actions.getStyleClass().add("card-actions");

        if (canManage()) {
            Button edit = new Button("Modifier");
            edit.getStyleClass().add("btn-ghost");
            edit.setOnAction(e -> openEditStrategieDialog(s));

            Button delete = new Button("Supprimer");
            // softer destructive style (outlined)
            delete.getStyleClass().add("btn-danger-outline");
            delete.setOnAction(e -> deleteStrategie(s));

            Button addObjective = new Button("Attribuer des objectifs");
            addObjective.getStyleClass().add("btn-ghost");
            addObjective.setOnAction(e -> openAddObjectiveDialog(s));

            actions.getChildren().addAll(edit, delete, addObjective);
        }


        // ===== CARD =====
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        card.getChildren().addAll(
                head,
                topRow,
                objChips,
                justificationLabel,
                actions
        );

        return card;
    }





    private void openAddDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/strategie/AddStrategie.fxml"));
            Parent content = loader.load();
            AddStrategieDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });
            c.setEditingStrategie(null);
            c.resetForm();
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir le formulaire: " + ex.getMessage());
        }
    }

    private void openEditStrategieDialog(Strategie strategie) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/strategie/AddStrategie.fxml"));
            Parent content = loader.load();
            AddStrategieDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });
            c.setEditingStrategie(strategie);
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir modification: " + ex.getMessage());
        }
    }

    private void openAddObjectiveDialog(Strategie strategie) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/objectif/AddObjectif.fxml"));
            Parent content = loader.load();
            AddObjectifController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });
            c.setStrategie(strategie);
            c.resetForm();
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir objectifs: " + ex.getMessage());
        }
    }

    private void openEditObjectiveDialog(Objective objective, Strategie strategie) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/objectif/AddObjectif.fxml"));
            Parent content = loader.load();
            AddObjectifController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });
            c.setStrategie(strategie);
            c.setEditingObjective(objective);
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir modification objectif: " + ex.getMessage());
        }
    }

    private void openObjectiveInfoDialog(Objective objective, Strategie strategie) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/objectif/ObjectiveInfoDialog.fxml"));
            Parent content = loader.load();
            ObjectiveInfoController controller = loader.getController();
            controller.setObjective(objective);
            controller.setOnClose(this::closeDialog);
            controller.setOnRefresh(this::refresh);
            controller.setOnEditRequested(o -> openEditObjectiveDialog(o, strategie));
            enableDrag(controller.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir detail objectif: " + ex.getMessage());
        }
    }

    private void deleteStrategie(Strategie s) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cette strategie ?");
        confirm.setContentText("ID: " + s.getId() + "\nCette action est irreversible.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            strategieService.supprimer(s);
            refresh();
        } catch (Exception ex) {
            showError("Suppression echouee: " + ex.getMessage());
        }
    }

    private void showDialog(Parent content) {
        modalBox.setTranslateX(0);
        modalBox.setTranslateY(0);
        if (content instanceof Region r) {
            r.setMaxWidth(Region.USE_PREF_SIZE);
            r.setMaxHeight(Region.USE_PREF_SIZE);
        }
        modalBox.getChildren().setAll(content);
        overlay.setManaged(true);
        overlay.setVisible(true);
    }

    private void closeDialog() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        modalBox.getChildren().clear();
        modalBox.setTranslateX(0);
        modalBox.setTranslateY(0);
    }

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

    private void refresh() {
        List<Strategie> list = strategieService.afficher();
        allObs.setAll(list);
        try {
            List<Objective> allObjectives = objectiveService.afficher();
            objectivesByStrategie = allObjectives.stream().collect(Collectors.groupingBy(Objective::getStrategieId));
        } catch (Exception ignored) {
            objectivesByStrategie = Map.of();
        }
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
        updateStats(list);
    }

    private void applyFilter(String q) {
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Strategie> filtered = allObs.stream()
                .filter(s -> search.isBlank()
                        || safe(s.getNomStrategie()).toLowerCase(Locale.ROOT).contains(search)
                        || (s.getProjet() != null && safe(s.getProjet().getTitleProj()).toLowerCase(Locale.ROOT).contains(search))
                        || (s.getStatut() != null && s.getStatut().toDb().toLowerCase(Locale.ROOT).contains(search)))
                .sorted(comparator)
                .toList();
        viewObs.setAll(filtered);
    }

    private void updateStats(List<Strategie> list) {
        int total = list == null ? 0 : list.size();
        long pending = list == null ? 0 : list.stream().filter(s -> s.getStatut() == StrategyStatut.EN_COURS).count();
        long accepted = list == null ? 0 : list.stream().filter(s -> s.getStatut() == StrategyStatut.ACCEPTEE).count();
        String success = total == 0 ? "0%" : Math.round((accepted * 100.0) / total) + "%";
        lblTotalStrategies.setText(String.valueOf(total));
        lblPending.setText(String.valueOf(pending));
        lblSuccess.setText(success);
    }

    private LocalDateTime getCreatedAtSafe(Strategie s) {
        return s == null ? null : s.getCreatedAt();
    }

    private int compareDateDesc(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    private int compareDateAsc(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    private boolean canManage() {
        UserRole role = SessionContext.getCurrentRole();
        return role == UserRole.ADMIN || role == UserRole.GERANT;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Gestion strategies");
        alert.showAndWait();
    }
}
