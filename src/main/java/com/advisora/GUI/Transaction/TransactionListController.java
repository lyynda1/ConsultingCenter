package com.advisora.GUI.Transaction;

import com.advisora.Model.invest.Investment;
import com.advisora.Model.invest.Transaction;
import com.advisora.Services.investment.InvestmentService;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.investment.TransactionService;
import com.advisora.enums.transactionStatut;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionListController {

    @FXML private ListView<Transaction> transactionList;
    @FXML private TextField txtSearch;
    @FXML private Label lblTotal;
    @FXML private Label lblPending;
    @FXML private Label lblSuccess;
    @FXML private Label lblTotalInvesti;
    @FXML private StackPane overlay;
    @FXML private VBox modalBox;

    private final TransactionService transactionService = new TransactionService();
    private final InvestmentService investmentService = new InvestmentService();
    private Map<Integer, Investment> investmentById = Collections.emptyMap();
    private final javafx.collections.ObservableList<Transaction> allObs = javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.ObservableList<Transaction> viewObs = javafx.collections.FXCollections.observableArrayList();
    private Comparator<Transaction> comparator = Comparator.comparing(Transaction::getDateTransac, Comparator.nullsLast(Comparator.reverseOrder()));
    private double dragOffsetX, dragOffsetY;
    private Runnable onOpenInvestissements = () -> {};

    @FXML
    public void initialize() {
        transactionList.setItems(viewObs);
        transactionList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildCard(t));
            }
        });

        txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));
        try {
            refresh();
        } catch (Exception ex) {
            showError("Chargement transactions impossible: " + ex.getMessage());
            viewObs.clear();
            updateStats(Collections.emptyList());
        }
    }

    public void setOnOpenInvestissements(Runnable r) {
        this.onOpenInvestissements = r != null ? r : () -> {};
    }

    @FXML
    private void openInvestissements(ActionEvent e) {
        onOpenInvestissements.run();
    }

    @FXML
    private void nouvelleTransaction(ActionEvent e) {
        openAddDialog();
    }

    @FXML
    private void onSearch(KeyEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortDateDesc(ActionEvent e) {
        comparator = Comparator.comparing(Transaction::getDateTransac, Comparator.nullsLast(Comparator.reverseOrder()));
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortDateAsc(ActionEvent e) {
        comparator = Comparator.comparing(Transaction::getDateTransac, Comparator.nullsFirst(Comparator.naturalOrder()));
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortMontantDesc(ActionEvent e) {
        comparator = Comparator.comparingDouble(Transaction::getMontantTransac).reversed();
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortMontantAsc(ActionEvent e) {
        comparator = Comparator.comparingDouble(Transaction::getMontantTransac);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortIdAsc(ActionEvent e) {
        comparator = Comparator.comparingInt(Transaction::getIdTransac);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    @FXML
    private void onSortIdDesc(ActionEvent e) {
        comparator = Comparator.comparingInt(Transaction::getIdTransac).reversed();
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    private VBox buildCard(Transaction t) {
        if (SessionContext.isClient()) {
            return buildClientCard(t);
        }
        Label title = new Label(safe(t.getType()));
        title.getStyleClass().add("card-title");

        Label statut = new Label(t.getStatut() == null ? "" : t.getStatut().name());
        statut.getStyleClass().add("badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, statut);

        String dateStr = t.getDateTransac() == null ? "-" : t.getDateTransac().toString();
        Label detail = new Label("Date: " + dateStr + " - Montant: " + t.getMontantTransac() + " - Inv# " + t.getIdInv());
        detail.getStyleClass().add("card-sub");

        HBox actions = new HBox(8);
        Button edit = new Button("Modifier");
        edit.getStyleClass().add("btn-ghost");
        edit.setOnAction(e -> openEditDialog(t));
        Button delete = new Button("Supprimer");
        delete.getStyleClass().add("btn-danger");
        delete.setOnAction(e -> deleteTransaction(t));
        actions.getChildren().addAll(edit, delete);

        VBox card = new VBox(10, head, detail, actions);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildClientCard(Transaction t) {
        Investment inv = investmentById.get(t.getIdInv());
        String invName = (inv != null && inv.getCommentaireInv() != null)
                ? safe(inv.getCommentaireInv())
                : ("Investissement #" + t.getIdInv());
        if (invName.length() > 80) invName = invName.substring(0, 77) + "...";
        String dureeStr = (inv != null && inv.getDureeInv() != null)
                ? inv.getDureeInv().toString()
                : "-";

        Label title = new Label(invName);
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label statutLabel = new Label(t.getStatut() == null ? "" : t.getStatut().name());
        statutLabel.getStyleClass().add("badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, statutLabel);

        Label detail = new Label("Montant: " + t.getMontantTransac() + "  •  Durée: " + dureeStr);
        detail.getStyleClass().add("card-sub");

        HBox actions = new HBox(8);
        boolean canEdit = t.getStatut() == transactionStatut.PENDING;
        if (canEdit) {
            Button edit = new Button("Modifier");
            edit.getStyleClass().add("btn-ghost");
            edit.setOnAction(e -> openEditDialog(t));
            actions.getChildren().add(edit);
        }
        Button delete = new Button("Supprimer");
        delete.getStyleClass().add("btn-danger");
        delete.setOnAction(e -> deleteTransaction(t));
        actions.getChildren().add(delete);

        VBox card = new VBox(10, head, detail, actions);
        card.getStyleClass().add("card");
        return card;
    }

    private void openAddDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/transaction/AddTransaction.fxml"));
            Parent content = loader.load();
            AddTransactionDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> { closeDialog(); refresh(); });
            c.initForAdd();
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir le formulaire: " + ex.getMessage());
        }
    }

    private void openEditDialog(Transaction t) {
        if (SessionContext.isClient() && t.getStatut() != transactionStatut.PENDING) {
            showError("Seules les transactions en attente (PENDING) peuvent être modifiées. Un admin a déjà validé cette transaction.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/transaction/AddTransaction.fxml"));
            Parent content = loader.load();
            AddTransactionDialogController c = loader.getController();
            c.setOnClose(this::closeDialog);
            c.setOnSaved(() -> { closeDialog(); refresh(); });
            c.initForEdit(t);
            enableDrag(c.getDragHandle(), modalBox);
            showDialog(content);
        } catch (Exception ex) {
            showError("Impossible d'ouvrir la modification: " + ex.getMessage());
        }
    }

    private void deleteTransaction(Transaction t) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer cette transaction ?");
        confirm.setContentText("ID: " + t.getIdTransac() + ". Cette action est irréversible.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            transactionService.supprimer(t);
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
        List<Transaction> list = transactionService.afficher();
        allObs.setAll(list);
        investmentById = investmentService.afficher().stream()
                .collect(Collectors.toMap(Investment::getIdInv, inv -> inv, (a, b) -> a));
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
        updateStats(list);
    }

    private void applyFilter(String q) {
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Transaction> filtered = allObs.stream()
                .filter(t -> search.trim().isEmpty()
                        || safe(t.getType()).toLowerCase(Locale.ROOT).contains(search)
                        || (t.getStatut() != null && t.getStatut().name().toLowerCase(Locale.ROOT).contains(search))
                        || String.valueOf(t.getIdTransac()).contains(search)
                        || String.valueOf(t.getIdInv()).contains(search)
                        || (t.getDateTransac() != null && t.getDateTransac().toString().contains(search)))
                .sorted(comparator)
                .collect(Collectors.toList());
        viewObs.setAll(filtered);
    }

    private void updateStats(List<Transaction> list) {
        int total = list == null ? 0 : list.size();
        long pending = list == null ? 0 : list.stream().filter(t -> t.getStatut() == transactionStatut.PENDING).count();
        long success = list == null ? 0 : list.stream().filter(t -> t.getStatut() == transactionStatut.SUCCESS).count();
        double totalInvesti;
        if (list == null || list.isEmpty()) {
            totalInvesti = 0;
        } else {
            var successOnly = list.stream().filter(t -> t.getStatut() == transactionStatut.SUCCESS);
            if (SessionContext.isClient()) {
                Set<Integer> clientInvIds = investmentService.getInvestmentsForClient(SessionContext.getCurrentUserId())
                        .stream().map(Investment::getIdInv).collect(Collectors.toSet());
                totalInvesti = successOnly
                        .filter(t -> clientInvIds.contains(t.getIdInv()))
                        .mapToDouble(Transaction::getMontantTransac)
                        .sum();
            } else {
                totalInvesti = successOnly.mapToDouble(Transaction::getMontantTransac).sum();
            }
        }
        if (lblTotal != null) lblTotal.setText(String.valueOf(total));
        if (lblPending != null) lblPending.setText(String.valueOf(pending));
        if (lblSuccess != null) lblSuccess.setText(String.valueOf(success));
        if (lblTotalInvesti != null) lblTotalInvesti.setText(String.format("%.2f TND", totalInvesti));
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Gestion Transactions");
        alert.showAndWait();
    }
}
