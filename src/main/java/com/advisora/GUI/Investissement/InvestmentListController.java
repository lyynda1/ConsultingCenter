package com.advisora.GUI.Investissement;

import com.advisora.Model.investment.Investment;
import com.advisora.Services.investment.InvestmentService;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.investment.TransactionService;
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
    }

    private void buildEvolutionChart() {
        if (chartContainer == null) return;
        chartContainer.getChildren().clear();
        try {
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel("Mois");
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel("Montant (TND)");
            LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setTitle("Évolution des Transactions (6 derniers mois)");
            chart.setLegendVisible(false);
            chart.setCreateSymbols(true);
            chart.setAnimated(false);
            chart.setPrefHeight(400);
            chart.getStyleClass().add("evolution-chart");

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Montants");
            var data = transactionService.getTransactionEvolutionLast6Months();
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
        String comment = safe(inv.getCommentaireInv());
        if (comment.length() > 80) comment = comment.substring(0, 77) + "...";
        Label title = new Label(comment);
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        String badge = String.format("%s %.0f - %.0f", safe(inv.getCurrencyInv()), inv.getBud_minInv(), inv.getBud_maxInv());
        Label statut = new Label(badge);
        statut.getStyleClass().add("badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, statut);

        VBox card = new VBox(10);
        card.getChildren().add(head);
        if (!SessionContext.isClient()) {
            Label detail = new Label("Projet #" + inv.getIdProj() + " • Utilisateur #" + inv.getIdUser());
            detail.getStyleClass().add("card-sub");
            Label idLabel = new Label("ID: " + inv.getIdInv());
            idLabel.getStyleClass().add("card-sub");
            card.getChildren().addAll(detail, idLabel);
        }
        HBox actions = new HBox(8);
        Button edit = new Button("Modifier");
        edit.getStyleClass().add("btn-ghost");
        edit.setOnAction(e -> openEditDialog(inv));
        Button delete = new Button("Supprimer");
        delete.getStyleClass().add("btn-danger");
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
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cet investissement ?");
        confirm.setContentText("ID: " + inv.getIdInv() + "\nCette action est irréversible.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            investmentService.supprimer(inv);
            refresh();
        } catch (Exception ex) {
            showError("Suppression échouée: " + ex.getMessage());
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
        List<Investment> list = investmentService.afficher();
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
        alert.setHeaderText("Gestion Investissements");
        alert.showAndWait();
    }
}
