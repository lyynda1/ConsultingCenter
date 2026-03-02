package com.advisora.GUI.Strategie;

import com.advisora.GUI.Objective.AddObjectifController;
import com.advisora.GUI.Objective.ObjectiveInfoController;
import com.advisora.Model.strategie.Objective;
import com.advisora.Model.strategie.Strategie;
import com.advisora.Services.strategie.ServiceObjective;
import com.advisora.Services.strategie.ServiceStrategie;
import com.advisora.Services.strategie.serviceSWOT;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.UserRole;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

public class StrategieListController {

    @FXML private ListView<Strategie> strategieList;
    @FXML private TextField txtSearch;
    @FXML private Label lblTotalStrategies;
    @FXML private Label lblPending;
    @FXML private Label lblSuccess;

    @FXML private StackPane overlay;
    @FXML private VBox modalBox;

    @FXML private CheckBox chkPendingOnly;
    @FXML private ComboBox<StrategyStatut> cmbStatusFilter;
    @FXML private ComboBox<String> cmbProjectFilter;

    private final ServiceStrategie strategieService = new ServiceStrategie();
    private final ServiceObjective objectiveService = new ServiceObjective();
    private final serviceSWOT swotService = new serviceSWOT();

    private final ObservableList<Strategie> allObs  = FXCollections.observableArrayList();
    private final ObservableList<Strategie> viewObs = FXCollections.observableArrayList();

    private Map<Integer, List<Objective>> objectivesByStrategie = Map.of();

    private Comparator<Strategie> comparator =
            Comparator.comparing(this::getCreatedAtSafe, this::compareDateDesc);

    private double dragOffsetX;
    private double dragOffsetY;

    // ============================
    // Countdown: robust lockedAt parser
    // Supports:
    //  - "2026-02-27 16:27:02"
    //  - "2026-02-27 16:27:02.0"
    //  - "2026-02-27 16:27:02.123"
    // ============================
    private static final DateTimeFormatter LOCKED_AT_FMT =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .optionalStart().appendPattern(".S").optionalEnd()
                    .optionalStart().appendPattern(".SS").optionalEnd()
                    .optionalStart().appendPattern(".SSS").optionalEnd()
                    .toFormatter(Locale.ROOT);

    private static LocalDateTime parseLockedAtSafe(String lockedAt) {
        if (lockedAt == null) return null;
        String x = lockedAt.trim();
        if (x.isEmpty()) return null;

        // If DB returns "2026-02-27 16:27:02.0" etc, formatter above handles it.
        // If DB returns something like "2026-02-27T16:27:02", you can normalize here.
        x = x.replace('T', ' ');
        try {
            return LocalDateTime.parse(x, LOCKED_AT_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseDaysSafe(Integer v) {
        if (v == null) return 0;
        return Math.max(v, 0);
    }

    private static LocalDateTime computeDeadlineFromLockedAt(Strategie s) {
        if (s == null) return null;
        if (s.getProjet() == null) return null;

        int days = s.getDureeTerme();     // ✅ int (DB int)
        if (days <= 0) return null;

        LocalDateTime lockedAt = s.getLockedAt(); // ✅ LocalDateTime
        if (lockedAt == null) return null;

        return lockedAt.plusDays(days);
    }

    private static long remainingSeconds(LocalDateTime deadline) {
        if (deadline == null) return 0;
        long sec = java.time.Duration.between(LocalDateTime.now(), deadline).getSeconds();
        return Math.max(sec, 0);
    }

    private static String formatRemaining(long sec) {
        if (sec <= 0) return "Expirée";
        long days = sec / 86400;
        long hours = (sec % 86400) / 3600;
        long mins = (sec % 3600) / 60;
        return String.format("%dj %dh %dm", days, hours, mins);
    }

    @FXML
    private void onOpenCharts() {
        openChartsDialog();
    }

    // ============================
    // Small wrapper so we never rely on lookup("#id") (more reliable)
    // ============================
    private static class CardWithCountdown {
        final VBox card;
        final Label countdownLabel;
        CardWithCountdown(VBox card, Label countdownLabel) {
            this.card = card;
            this.countdownLabel = countdownLabel;
        }
    }

    @FXML
    public void initialize() {

        strategieList.setItems(viewObs);
        strategieList.setFixedCellSize(-1);

        // ---- Status filter ----
        if (cmbStatusFilter != null) {
            cmbStatusFilter.setItems(FXCollections.observableArrayList(StrategyStatut.values()));
            cmbStatusFilter.setPromptText("Statut");
            cmbStatusFilter.valueProperty().addListener((obs, oldV, newV) ->
                    applyFilter(txtSearch == null ? "" : txtSearch.getText())
            );
        }

        // ---- Project filter ----
        if (cmbProjectFilter != null) {
            cmbProjectFilter.setItems(FXCollections.observableArrayList("Tous"));
            cmbProjectFilter.setValue("Tous");
            cmbProjectFilter.valueProperty().addListener((obs, oldV, newV) ->
                    applyFilter(txtSearch == null ? "" : txtSearch.getText())
            );
        }

        // ---- List cell factory (cards + countdown) ----
        /**
         * ListCell implementation used for strategies (cards + countdown).
         * It uses the helper buildCard(Strategie) that returns a CardWithCountdown
         * (the VBox card and the Label that shows the remaining time).
         */
        strategieList.setCellFactory(lv -> new ListCell<>() {

            /** One timer per cell – keeps the countdown ticking. */
            private Timeline countdownTimeline;

            {
                // only graphic, no text
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                prefWidthProperty().bind(lv.widthProperty().subtract(20));
            }

            @Override
            protected void updateItem(Strategie s, boolean empty) {
                super.updateItem(s, empty);

                // ✅ 1) stop old timer because ListView cells are reused
                if (countdownTimeline != null) {
                    countdownTimeline.stop();
                    countdownTimeline = null;
                }

                // ✅ 2) empty cell
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }

                // ✅ 3) build UI card + get the countdown label directly (no lookup)
                CardWithCountdown cw = buildCard(s);
                VBox card = cw.card;
                Label countdownLbl = cw.countdownLabel;

                card.maxWidthProperty().bind(prefWidthProperty());
                card.setFillWidth(true);
                setGraphic(card);

                // ✅ 4) compute deadline using LOCKED_AT (start) + dureeTerme (days)
                // countdown should show ONLY when a project is linked and lockedAt exists and dureeTerme>0
                LocalDateTime deadline = computeDeadlineFromLockedAt(s);

                if (deadline == null) {
                    // hide if we can't compute (no project / no lockedAt / invalid days)
                    countdownLbl.setVisible(false);
                    countdownLbl.setManaged(false);
                    countdownLbl.setText("");
                    return;
                }

                // show label
                countdownLbl.setManaged(true);
                countdownLbl.setVisible(true);

                // ✅ 5) tick function
                Runnable tick = () -> {
                    long secsLeft = remainingSeconds(deadline);

                    if (secsLeft <= 0) {
                        countdownLbl.setText("Durée restante : Expirée");
                        // optional styling class
                        countdownLbl.getStyleClass().removeAll("countdown-ok");
                        if (!countdownLbl.getStyleClass().contains("countdown-expired")) {
                            countdownLbl.getStyleClass().add("countdown-expired");
                        }
                    } else {
                        countdownLbl.setText("Durée restante : " + formatRemaining(secsLeft));
                        // optional styling class
                        countdownLbl.getStyleClass().removeAll("countdown-expired");
                        if (!countdownLbl.getStyleClass().contains("countdown-ok")) {
                            countdownLbl.getStyleClass().add("countdown-ok");
                        }
                    }
                };

                // initial update immediately
                tick.run();

                // ✅ 6) start timeline (updates every second)
                countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                    tick.run();

                    // stop automatically once expired
                    if (remainingSeconds(deadline) <= 0) {
                        countdownTimeline.stop();
                    }
                }));
                countdownTimeline.setCycleCount(Animation.INDEFINITE);
                countdownTimeline.play();
            }

            /* -----------------------------------------------------------------------
             * Helpers – keep them in the controller so all other methods can use them
             * ----------------------------------------------------------------------- */
            private void hideCountdown(Label lbl) {
                lbl.setVisible(false);
                lbl.setManaged(false);
                lbl.setText("");   // clear any stale text
            }
        });


        // ---- Search ----
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));
        }

        // ---- Admin toggle ----
        boolean isAdmin = (SessionContext.getCurrentRole() == UserRole.ADMIN);
        if (chkPendingOnly != null) {
            chkPendingOnly.setVisible(isAdmin);
            chkPendingOnly.setManaged(isAdmin);
            chkPendingOnly.setSelected(false);
            chkPendingOnly.selectedProperty().addListener((o, oldV, newV) ->
                    applyFilter(txtSearch == null ? "" : txtSearch.getText())
            );
        }

        // ---- Initial load ----
        try {
            refresh();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Chargement strategies impossible: " + ex.getMessage());
            viewObs.clear();
            if (lblTotalStrategies != null) lblTotalStrategies.setText("0");
            if (lblPending != null) lblPending.setText("0");
            if (lblSuccess != null) lblSuccess.setText("0%");
        }
    }

    // ============================
    // UI Handlers
    // ============================

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
    private void onTogglePendingOnly(ActionEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    public void onFilterStatus(ActionEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    public void onFilterProject(ActionEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    public void onRefresh(ActionEvent e) {
        try {
            refresh();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Rafraîchissement impossible: " + ex.getMessage());
        }
    }

    // ============================
    // Refresh (DB -> UI)
    // ============================
    private void resetFilters() {
        if (cmbProjectFilter != null) {
            if (!cmbProjectFilter.getItems().contains("Tous")) {
                cmbProjectFilter.getItems().add(0, "Tous");
            }
            cmbProjectFilter.setValue("Tous");
        }
        if (cmbStatusFilter != null) {
            cmbStatusFilter.setValue(null);
        }
        if (chkPendingOnly != null) {
            chkPendingOnly.setSelected(false);
        }
    }
    private void reloadStrategies() {
        List<Strategie> allStrategies = strategieService.afficher();
        allObs.setAll(allStrategies); // ou viewObs selon ton design
    }

    private void refresh() throws Exception {

        // 1) Reload all strategies
        List<Strategie> allStrategies = strategieService.afficher();
        allObs.setAll(allStrategies);

        // 2) Reload objectives map
        Map<Integer, List<Objective>> map = new HashMap<>();
        for (Strategie s : allStrategies) {
            map.put(s.getId(), strategieService.getObjectivesByStrategie(s.getId())); // nécessite la méthode
        }
        objectivesByStrategie = map;

        // 3) Reset filters safely
        resetFilters();

        // 4) Apply filter + stats
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
        updateStats(viewObs);
    }



    // ============================
    // Filtering
    // ============================

    private void applyFilter(String q) {
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        boolean isAdmin = (SessionContext.getCurrentRole() == UserRole.ADMIN);
        boolean pendingOnly = isAdmin && chkPendingOnly != null && chkPendingOnly.isSelected();

        StrategyStatut statusFilter = (cmbStatusFilter != null) ? cmbStatusFilter.getValue() : null;
        String projectFilter = (cmbProjectFilter != null) ? cmbProjectFilter.getValue() : null;

        if (projectFilter != null && projectFilter.equalsIgnoreCase("Tous")) {
            projectFilter = null;
        }
        String finalProjectFilter = projectFilter;

        List<Strategie> filtered = allObs.stream()
                .filter(s -> !pendingOnly || s.getStatut() == StrategyStatut.EN_ATTENTE)
                .filter(s -> statusFilter == null || s.getStatut() == statusFilter)
                .filter(s -> finalProjectFilter == null ||
                        (s.getProjet() != null &&
                                safe(s.getProjet().getTitleProj()).equalsIgnoreCase(finalProjectFilter)))
                .filter(s ->
                        search.isBlank()
                                || safe(s.getNomStrategie()).toLowerCase(Locale.ROOT).contains(search)
                                || (s.getProjet() != null && safe(s.getProjet().getTitleProj()).toLowerCase(Locale.ROOT).contains(search))
                                || (s.getStatut() != null && s.getStatut().toDb().toLowerCase(Locale.ROOT).contains(search))
                )
                .sorted(comparator)
                .toList();

        viewObs.setAll(filtered);
    }

    // ============================
    // Sort handlers
    // ============================

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

    // ============================
    // Card UI
    // ============================

    private CardWithCountdown buildCard(Strategie s) {

        Label title = new Label(safe(s.getNomStrategie()));
        title.getStyleClass().add("card-title");

        String statusText = (s.getStatut() == null) ? "" : s.getStatut().toDb();
        String statusIcon = (s.getStatut() == null) ? "" : switch (s.getStatut()) {
            case ACCEPTEE -> "✅ ";
            case REFUSEE -> "⛔ ";
            case EN_COURS -> "⏳ ";
            case NON_AFFECTEE -> "⚪ ";
            case EN_ATTENTE -> "🕒 ";
            default -> "";
        };

        Label statut = new Label(statusIcon + statusText);
        statut.getStyleClass().addAll("badge", "status-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox head = new HBox(10, title, spacer, statut);
        head.getStyleClass().add("card-head");

        // ---- META LEFT ----
        Label projet = new Label("Projet associé : " +
                (s.getProjet() == null ? "-" : safe(s.getProjet().getTitleProj())));
        projet.getStyleClass().add("card-meta");

        Label date = new Label("Date de création : " +
                (s.getCreatedAt() == null ? "-" : s.getCreatedAt().toLocalDate()));
        date.getStyleClass().add("card-meta");

        Label type = new Label("Type : " +
                (s.getTypeStrategie() == null ? "-" : safe(s.getTypeStrategie().name())));
        type.getStyleClass().add("card-meta");

        Integer d = s.getDureeTerme();
        Label duree = new Label("Durée du terme : " + (d == null ? "-" : d + " jours"));
        duree.getStyleClass().add("card-meta");

        Label lockedAtLbl = new Label("LockedAt : " + (s.getLockedAt() == null ? "-" : s.getLockedAt().toString()));
        lockedAtLbl.getStyleClass().add("card-meta");

        // Countdown label (used by cell timer)
        Label countdown = new Label("");
        countdown.getStyleClass().add("card-meta");
        countdown.setWrapText(true);
        countdown.setVisible(false);
        countdown.setManaged(false);

        // SWOT info
        int swotCount = swotService.countByStrategie(s.getId());
        Label swotInfo = new Label(swotCount == 0 ? "SWOT : -" : "SWOT : " + swotCount + " points");
        swotInfo.getStyleClass().add("card-meta");

        VBox metaLeft = new VBox(6, projet, date, type, duree, swotInfo, lockedAtLbl, countdown);
        metaLeft.getStyleClass().add("card-meta-col");

        // ---- METRICS RIGHT ----
        String budgetText = (s.getBudgetTotal() == 0) ? "-" : String.format("%,.0f DT", s.getBudgetTotal());
        String gainText   = (s.getGainEstime() == 0) ? "-" : String.format("%,.0f DT", s.getGainEstime());

        Label budget = new Label("Budget : " + budgetText);
        budget.getStyleClass().add("metric-line");

        Label gain = new Label("Gain estimé : " + gainText);
        gain.getStyleClass().add("metric-line");

        Label roi = new Label("ROI : -");
        roi.getStyleClass().add("roi-value");

        if (s.getGainEstime() != 0 && s.getBudgetTotal() != 0) {
            double roiValue = strategieService.CalculROI(s.getGainEstime(), s.getBudgetTotal()) * 100.0;
            roi.setText(String.format("ROI : %+,.0f%%", roiValue));
        }

        VBox metricsRight = new VBox(6, budget, gain, roi);
        metricsRight.getStyleClass().add("metrics-col");

        HBox topRow = new HBox(22, metaLeft, metricsRight);
        topRow.getStyleClass().add("card-top-row");
        HBox.setHgrow(metaLeft, Priority.ALWAYS);

        // ---- OBJECTIVES ----
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

        // ---- JUSTIF ----
        Label justificationLabel = new Label();
        justificationLabel.getStyleClass().add("justification");
        justificationLabel.setWrapText(true);
        justificationLabel.setMaxWidth(Double.MAX_VALUE);

        String j = s.getJustification();
        boolean hasJustif = j != null && !j.trim().isEmpty();
        justificationLabel.setText(hasJustif ? "Justification : " + j.trim() : "");
        justificationLabel.setVisible(hasJustif);
        justificationLabel.setManaged(hasJustif);

        // ---- ACTIONS ----
        HBox actions = new HBox(10);
        actions.getStyleClass().add("card-actions");

        // SWOT button always visible
        Button swotBtn = new Button("SWOT");
        swotBtn.getStyleClass().add("btn-ghost");
        swotBtn.setOnAction(e -> openSwotDialog(s));
        actions.getChildren().add(swotBtn);

        if (canManage()) {
            Button edit = new Button("Modifier");
            edit.getStyleClass().add("btn-ghost");
            edit.setOnAction(e -> openEditStrategieDialog(s));

            Button delete = new Button("Supprimer");
            delete.getStyleClass().add("btn-danger-outline");
            delete.setOnAction(e -> deleteStrategie(s));

            Button addObjective = new Button("Attribuer des objectifs");
            addObjective.getStyleClass().add("btn-ghost");
            addObjective.setOnAction(e -> openAddObjectiveDialog(s));

            actions.getChildren().addAll(edit, delete, addObjective);
        }

        if (isAdmin() && s.getStatut() == StrategyStatut.EN_ATTENTE) {
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            actions.getChildren().add(grow);

            Button accept = new Button("Accepter");
            accept.getStyleClass().add("btn-success");
            accept.setOnAction(e -> acceptStrategie(s));

            Button refuse = new Button("Refuser");
            refuse.getStyleClass().add("btn-danger");
            refuse.setOnAction(e -> refuseStrategie(s));

            actions.getChildren().addAll(accept, refuse);
        }

        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.getChildren().addAll(head, topRow, objChips, justificationLabel, actions);

        return new CardWithCountdown(card, countdown);
    }
    // ============================
    // Dialogs / Actions
    // ============================

    private void openAddDialog() {
        try {
            var url = getClass().getResource("/views/strategie/AddStrategie.fxml");
            if (url == null) {
                showError("FXML introuvable: /views/strategie/AddStrategie.fxml");
                return;
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent content = loader.load();

            AddStrategieDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                try { refresh(); } catch (Exception ex) { showError(ex.getMessage()); }
            });

            c.setEditingStrategie(null);
            c.resetForm();

            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
            Throwable root = ex;
            while (root.getCause() != null) root = root.getCause();
            showError("Impossible d'ouvrir le formulaire:\n" + root.getMessage());
        }
    }
    private void openChartsDialog() {
        try {
            var url = getClass().getResource("/views/strategie/StrategyChartsDialog.fxml");
            if (url == null) { showError("FXML introuvable: /views/strategie/StrategyChartsDialog.fxml"); return; }

            FXMLLoader loader = new FXMLLoader(url);
            Parent content = loader.load();

            StrategyChartsDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setStrategies(new ArrayList<>(allObs));

            // ✅ click point -> open edit dialog (or info)
            c.setOnStrategyClicked(s -> openEditStrategieDialog(s));

            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible d'ouvrir le chart: " + ex.getMessage());
        }
    }
    private void openSwotDialog(Strategie strategie) {
        try {
            var url = getClass().getResource("/views/strategie/SwotDialog.fxml");
            if (url == null) {
                showError("FXML introuvable: /views/strategie/SwotDialog.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent content = loader.load();

            SwotDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);

            // optionnel: quand on ajoute/modifie, refresh pour mettre à jour "SWOT : x points"
            c.setOnSaved(() -> {
                try { refresh(); } catch (Exception ex) { showError(ex.getMessage()); }
            });

            c.setStrategie(strategie);

            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible d'ouvrir SWOT: " + ex.getMessage());
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
                try { refresh(); } catch (Exception ex) { showError(ex.getMessage()); }
            });

            c.setEditingStrategie(strategie);
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
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
                try { refresh(); } catch (Exception ex) { showError(ex.getMessage()); }
            });

            c.setStrategie(strategie);
            c.resetForm();

            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
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
                try { refresh(); } catch (Exception ex) { showError(ex.getMessage()); }
            });

            c.setStrategie(strategie);
            c.setEditingObjective(objective);

            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
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

            controller.setOnRefresh(() -> {
                try { refresh(); } catch (Exception ex) { showError(ex.getMessage()); }
            });

            controller.setOnEditRequested(o -> openEditObjectiveDialog(o, strategie));

            enableDrag(controller.getDragHandle(), modalBox);
            showDialog(content);

        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible d'ouvrir detail objectif: " + ex.getMessage());
        }
    }

    private void deleteStrategie(Strategie s) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cette strategie ?");
        confirm.setContentText("ID: " + s.getId() + "\nCette action est irreversible.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            strategieService.supprimer(s);
            refresh();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Suppression echouee: " + ex.getMessage());
        }
    }

    private void acceptStrategie(Strategie s) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Accepter cette stratégie ?\nID: " + s.getId(),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Validation Admin");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            strategieService.updateStatut(s.getId(), StrategyStatut.NON_AFFECTEE);
            refresh();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible d'accepter: " + ex.getMessage());
        }
    }

    private void refuseStrategie(Strategie s) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Refus Admin");
        dialog.setHeaderText("Refuser cette stratégie");
        dialog.setContentText("Motif (optionnel):");

        String motif = dialog.showAndWait().orElse("").trim();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Confirmer le refus ?\nID: " + s.getId(),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Validation Admin");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            strategieService.applyDecision(s.getId(), false, motif, true);
            strategieService.updateStatut(s.getId(), StrategyStatut.REFUSEE);
            refresh();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Impossible de refuser: " + ex.getMessage());
        }
    }

    // ============================
    // Overlay / drag
    // ============================

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
        if (handle == null || draggable == null) return;

        handle.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX() - draggable.getTranslateX();
            dragOffsetY = e.getSceneY() - draggable.getTranslateY();
        });
        handle.setOnMouseDragged(e -> {
            draggable.setTranslateX(e.getSceneX() - dragOffsetX);
            draggable.setTranslateY(e.getSceneY() - dragOffsetY);
        });
    }

    // ============================
    // Helpers
    // ============================

    private boolean isAdmin() {
        return SessionContext.getCurrentRole() == UserRole.ADMIN;
    }

    private boolean canManage() {
        UserRole role = SessionContext.getCurrentRole();
        return role == UserRole.ADMIN || role == UserRole.GERANT;
    }

    private void updateStats(List<Strategie> list) {
        int total = list == null ? 0 : list.size();
        long pending = list == null ? 0 : list.stream().filter(s -> s.getStatut() == StrategyStatut.EN_COURS).count();
        long accepted = list == null ? 0 : list.stream().filter(s -> s.getStatut() == StrategyStatut.ACCEPTEE).count();

        String success = total == 0 ? "0%" : Math.round((accepted * 100.0) / total) + "%";

        if (lblTotalStrategies != null) lblTotalStrategies.setText(String.valueOf(total));
        if (lblPending != null) lblPending.setText(String.valueOf(pending));
        if (lblSuccess != null) lblSuccess.setText(success);
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Gestion strategies");
        alert.showAndWait();
    }

    // ✅ THIS is the countdown core used by the cell:
    private static LocalDateTime computeDeadline(Strategie s) {
        return computeDeadlineFromLockedAt(s);
    }
}