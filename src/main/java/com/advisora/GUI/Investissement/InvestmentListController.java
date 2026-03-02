package com.advisora.GUI.Investissement;

import com.advisora.Model.investment.Investment;
import com.advisora.Services.investment.InvestmentService;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.investment.TransactionService;
import com.advisora.utils.i18n.I18n;
import com.advisora.utils.i18n.LangBus;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class InvestmentListController {

    @FXML private ListView<Investment> investmentList;
    @FXML private TextField txtSearch;
    @FXML private Label lblTotal;
    @FXML private Label lblDevises;
    @FXML private Label lblProjets;
    @FXML private VBox chartContainer;
    @FXML private StackPane overlay;
    @FXML private VBox modalBox;
    @FXML private Label pageTitle;
    @FXML private Button btnTransactions;
    @FXML private Button btnExchangeRate;
    @FXML private Button btnMacro;
    @FXML private Button btnNewInvestment;
    @FXML private MenuButton btnFilter;

    @FXML private MenuItem miCommentAsc;
    @FXML private MenuItem miCommentDesc;
    @FXML private MenuItem miBudgetAsc;
    @FXML private MenuItem miBudgetDesc;
    @FXML private MenuItem miIdAsc;
    @FXML private MenuItem miIdDesc;

    private final InvestmentService investmentService = new InvestmentService();
    private final TransactionService transactionService = new TransactionService();
    private final javafx.collections.ObservableList<Investment> allObs = javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.ObservableList<Investment> viewObs = javafx.collections.FXCollections.observableArrayList();
    private Comparator<Investment> comparator = Comparator.comparingInt(Investment::getIdInv);
    private double dragOffsetX, dragOffsetY;
    private Runnable onOpenTransactions = () -> {};

    @FXML
    public void initialize() {
        investmentList.setItems(viewObs);
        investmentList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Investment inv, boolean empty) {
                super.updateItem(inv, empty);
                if (empty || inv == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildCard(inv));
            }
        });

        txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));
        try {
            refresh();
        } catch (Exception ex) {
            showError("Chargement investissements impossible: " + ex.getMessage());
            viewObs.clear();

        }

        if (pageTitle != null) bindI18n(pageTitle, "invest.page.title");
        if (btnTransactions != null) bindI18n(btnTransactions, "invest.btn.transactions");
        if (btnExchangeRate != null) bindI18n(btnExchangeRate, "invest.btn.exchangeRate");
        if (btnMacro != null) bindI18n(btnMacro, "invest.btn.macro");
        if (btnNewInvestment != null) bindI18n(btnNewInvestment, "invest.btn.new");
        if (btnFilter != null) btnFilter.setText(I18n.tr("invest.btn.filter"));

        if (txtSearch != null) bindPrompt(txtSearch, "invest.search.prompt");

        if (miCommentAsc != null) miCommentAsc.setText(I18n.tr("invest.sort.comment.asc"));
        if (miCommentDesc != null) miCommentDesc.setText(I18n.tr("invest.sort.comment.desc"));
        if (miBudgetAsc != null) miBudgetAsc.setText(I18n.tr("invest.sort.budget.asc"));
        if (miBudgetDesc != null) miBudgetDesc.setText(I18n.tr("invest.sort.budget.desc"));
        if (miIdAsc != null) miIdAsc.setText(I18n.tr("invest.sort.id.asc"));
        if (miIdDesc != null) miIdDesc.setText(I18n.tr("invest.sort.id.desc"));

        // Re-apply menu texts on language change
        LangBus.localeProperty().addListener((obs, o, n) -> {
            if (btnFilter != null) btnFilter.setText(I18n.tr("invest.btn.filter"));
            if (miCommentAsc != null) miCommentAsc.setText(I18n.tr("invest.sort.comment.asc"));
            if (miCommentDesc != null) miCommentDesc.setText(I18n.tr("invest.sort.comment.desc"));
            if (miBudgetAsc != null) miBudgetAsc.setText(I18n.tr("invest.sort.budget.asc"));
            if (miBudgetDesc != null) miBudgetDesc.setText(I18n.tr("invest.sort.budget.desc"));
            if (miIdAsc != null) miIdAsc.setText(I18n.tr("invest.sort.id.asc"));
            if (miIdDesc != null) miIdDesc.setText(I18n.tr("invest.sort.id.desc"));
            buildEvolutionChart(); // chart labels/title update too
        });

        investmentList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Investment inv, boolean empty) {
                super.updateItem(inv, empty);
                if (empty || inv == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildCard(inv));
            }
        });

        txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));

        try {
            refresh();
        } catch (Exception ex) {
            showError(I18n.tr("invest.error.load") + ": " + ex.getMessage());
            viewObs.clear();
        }
    }

    private static final String DB_SOURCE_LANG = "fr";

    private String targetLang() {
        return com.advisora.utils.AppLanguage.ltTargetCode(); // "fr" / "en" / "ar" ...
    }

    private void translateDbAsync(String raw, java.util.function.Consumer<String> onUi) {
        String x = safe(raw);
        if (x.isBlank()) { onUi.accept(""); return; }
        com.advisora.AppServices.TRANSLATOR.translateAsync(x, DB_SOURCE_LANG, targetLang(), onUi);
    }

    private void bindI18n(Labeled c, String key) {
        Runnable apply = () -> c.setText(I18n.tr(key));
        apply.run();
        LangBus.localeProperty().addListener((obs, o, n) -> apply.run());
    }

    private void bindPrompt(TextField tf, String key) {
        Runnable apply = () -> tf.setPromptText(I18n.tr(key));
        apply.run();
        LangBus.localeProperty().addListener((obs, o, n) -> apply.run());
    }

    /** DB text -> translate on locale change */
    private void bindDbText(Label lbl, String rawDbText) {
        lbl.setUserData(rawDbText);
        Runnable apply = () -> {
            String raw = safe((String) lbl.getUserData());
            lbl.setText(raw);
            translateDbAsync(raw, lbl::setText);
        };
        apply.run();
        LangBus.localeProperty().addListener((obs, o, n) -> apply.run());
    }






    private void buildEvolutionChart() {
        if (chartContainer == null) return;
        chartContainer.getChildren().clear();

        try {
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel(I18n.tr("invest.chart.x"));

            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel(I18n.tr("invest.chart.y"));

            LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setTitle(I18n.tr("invest.chart.title"));
            chart.setLegendVisible(false);
            chart.setCreateSymbols(true);
            chart.setAnimated(false);
            chart.setPrefHeight(400);
            chart.getStyleClass().add("evolution-chart");

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(I18n.tr("invest.chart.series"));

            var data = SessionContext.isClient()
                    ? transactionService.getTransactionEvolutionLast6MonthsForClient(SessionContext.getCurrentUserId())
                    : transactionService.getTransactionEvolutionLast6Months();

            for (var e : data.entrySet()) {
                series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            }

            chart.getData().add(series);
            chartContainer.getChildren().add(chart);

        } catch (Exception ex) {
            chartContainer.getChildren().clear();
        }
    }

    public void setOnOpenTransactions(Runnable r) {
        this.onOpenTransactions = r != null ? r : () -> {};
    }

    @FXML
    private void openTransactions(ActionEvent e) {
        onOpenTransactions.run();
    }

    @FXML
    private void nouvelInvestissement(ActionEvent e) {
        openAddDialog();
    }

    @FXML
    private void openExchangeRate(ActionEvent e) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/investissement/ExchangeRate.fxml"));
            Parent content = loader.load();
            ExchangeRateController c = loader.getController();
            c.setOnClose(this::closeDialog);
            enableDrag(((VBox) content).lookup("#dragHandle"), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir le taux de change : " + ex.getMessage());
        }
    }

    @FXML
    private void openMacroAnalysis(ActionEvent e) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/investissement/MacroAnalysis.fxml"));
            Parent content = loader.load();
            MacroAnalysisController c = loader.getController();
            c.setOnClose(this::closeDialog);
            enableDrag(((VBox) content).lookup("#dragHandle"), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir l'analyse macro : " + ex.getMessage());
        }
    }

    @FXML
    private void onSearch(KeyEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortCommentaireAsc(ActionEvent e) {
        comparator = Comparator.comparing(inv -> safe(inv.getCommentaireInv()).toLowerCase(Locale.ROOT));
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortCommentaireDesc(ActionEvent e) {
        comparator = Comparator.comparing((Investment inv) -> safe(inv.getCommentaireInv()).toLowerCase(Locale.ROOT)).reversed();
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortBudgetAsc(ActionEvent e) {
        comparator = Comparator.comparingDouble(Investment::getBud_minInv);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortBudgetDesc(ActionEvent e) {
        comparator = Comparator.comparingDouble(Investment::getBud_maxInv).reversed();
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortIdAsc(ActionEvent e) {
        comparator = Comparator.comparingInt(Investment::getIdInv);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortIdDesc(ActionEvent e) {
        comparator = Comparator.comparingInt(Investment::getIdInv).reversed();
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    private VBox buildCard(Investment inv) {

        // Comment (DB -> translate)
        String rawComment = safe(inv.getCommentaireInv());
        String shortRaw = rawComment.length() > 80 ? rawComment.substring(0, 77) + "..." : rawComment;

        Label title = new Label();
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        bindDbText(title, shortRaw);

        // badge contains currency (DB) + numbers (no translate for numbers)
        Label statut = new Label();
        statut.getStyleClass().add("badge");
        String rawCurrency = safe(inv.getCurrencyInv());

        Runnable badgeApply = () -> {
            String min = String.format(Locale.US, "%.0f", inv.getBud_minInv());
            String max = String.format(Locale.US, "%.0f", inv.getBud_maxInv());

            // show raw first
            statut.setText(rawCurrency + " " + min + " - " + max);

            // translate currency if needed (optional)
            translateDbAsync(rawCurrency, trCur -> statut.setText(trCur + " " + min + " - " + max));
        };
        badgeApply.run();
        LangBus.localeProperty().addListener((obs, o, n) -> badgeApply.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, statut);

        VBox card = new VBox(10);
        card.getChildren().add(head);

        if (!SessionContext.isClient()) {
            Label detail = new Label();
            detail.getStyleClass().add("card-sub");

            Label idLabel = new Label();
            idLabel.getStyleClass().add("card-sub");

            Runnable detailApply = () -> {
                detail.setText(
                        I18n.tr("invest.card.project") + " #" + inv.getIdProj() +
                                " • " +
                                I18n.tr("invest.card.user") + " #" + inv.getIdUser()
                );
                idLabel.setText(I18n.tr("invest.card.id") + ": " + inv.getIdInv());
            };
            detailApply.run();
            LangBus.localeProperty().addListener((obs, o, n) -> detailApply.run());

            card.getChildren().addAll(detail, idLabel);
        }

        HBox actions = new HBox(8);

        Button edit = new Button();
        edit.getStyleClass().add("btn-ghost");
        bindI18n(edit, "invest.card.edit");
        edit.setOnAction(e -> openEditDialog(inv));

        Button delete = new Button();
        delete.getStyleClass().add("btn-danger");
        bindI18n(delete, "invest.card.delete");
        delete.setOnAction(e -> deleteInvestment(inv));

        actions.getChildren().addAll(edit, delete);
        card.getChildren().add(actions);

        card.getStyleClass().add("card");
        return card;
    }
    private void openAddDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/investissement/AddInvestissement.fxml"));
            Parent content = loader.load();
            AddInvestissementDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });
            c.initForAdd();
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir le formulaire: " + ex.getMessage());
        }
    }

    private void openEditDialog(Investment inv) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/investissement/AddInvestissement.fxml"));
            Parent content = loader.load();
            AddInvestissementDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> {
                closeDialog();
                refresh();
            });
            c.initForEdit(inv);
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir la modification: " + ex.getMessage());
        }
    }

    private void deleteInvestment(Investment inv) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(I18n.tr("common.confirmation"));
        confirm.setHeaderText(I18n.tr("invest.delete.header"));
        confirm.setContentText(I18n.tr("invest.delete.content")
                .replace("{id}", String.valueOf(inv.getIdInv())));

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            investmentService.supprimer(inv);
            refresh();
        } catch (Exception ex) {
            showError(I18n.tr("invest.error.delete") + ": " + ex.getMessage());
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
        List<Investment> list;
        if (SessionContext.isClient()) {
            list = investmentService.getInvestmentsForClient(SessionContext.getCurrentUserId());
        } else {
            list = investmentService.afficher();
        }
        allObs.setAll(list);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());

        buildEvolutionChart();
    }

    private void applyFilter(String q) {
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Investment> filtered = allObs.stream()
                .filter(inv -> search.trim().isEmpty()
                        || safe(inv.getCommentaireInv()).toLowerCase(Locale.ROOT).contains(search)
                        || safe(inv.getCurrencyInv()).toLowerCase(Locale.ROOT).contains(search)
                        || String.valueOf(inv.getIdInv()).contains(search)
                        || String.valueOf(inv.getIdProj()).contains(search)
                        || String.valueOf(inv.getIdUser()).contains(search))
                .sorted(comparator)
                .collect(Collectors.toList());
        viewObs.setAll(filtered);
    }



    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(I18n.tr("invest.error.header"));
        alert.showAndWait();
    }
}

