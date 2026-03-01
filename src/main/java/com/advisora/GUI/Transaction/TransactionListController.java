package com.advisora.GUI.Transaction;

import com.advisora.utils.SceneThemeApplier;

import com.advisora.Model.investment.Investment;
import com.advisora.Model.investment.Transaction;
import com.advisora.Services.investment.InvestmentService;
import com.advisora.Services.investment.TransactionService;
import com.advisora.Services.investment.TransactionPdfExportService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.transactionStatut;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
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
    private final TransactionPdfExportService pdfExportService = new TransactionPdfExportService();
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

        Label detail = new Label("Montant: " + t.getMontantTransac() + "  â€¢  DurÃ©e: " + dureeStr);
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
            showError("Seules les transactions en attente (PENDING) peuvent Ãªtre modifiÃ©es. Un admin a dÃ©jÃ  validÃ© cette transaction.");
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
        confirm.setContentText("ID: " + t.getIdTransac() + ". Cette action est irrÃ©versible.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            transactionService.supprimer(t);
            refresh();
        } catch (Exception ex) {
            showError("Suppression Ã©chouÃ©e: " + ex.getMessage());
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
        List<Transaction> list;
        if (SessionContext.isClient()) {
            list = transactionService.getTransactionsForClient(SessionContext.getCurrentUserId());
        } else {
            list = transactionService.afficher();
        }
        allObs.setAll(list);
        List<Investment> invList = SessionContext.isClient()
                ? investmentService.getInvestmentsForClient(SessionContext.getCurrentUserId())
                : investmentService.afficher();
        investmentById = invList.stream()
                .collect(Collectors.toMap(Investment::getIdInv, inv -> inv, (a, b) -> a));
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
        updateStats(list);
    }

    @FXML
    private void onOpenHistorique(ActionEvent e) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/transaction/TransactionHistory.fxml"));
            Parent root = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Historique des Transactions");
            SceneThemeApplier.setScene(stage, root);
            stage.initOwner(transactionList.getScene().getWindow());
            stage.show();
        } catch (Exception ex) {
            showError("Impossible d'ouvrir l'historique: " + ex.getMessage());
        }
    }

    @FXML
    private void onExportPdf(ActionEvent e) {
        try {
            List<Transaction> rows = new ArrayList<>(allObs);
            if (rows.isEmpty()) {
                showError("Aucune transaction Ã  exporter.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Exporter historique des transactions (PDF)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            String ts = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
            chooser.setInitialFileName("transactions_" + ts + ".pdf");
            File selected = chooser.showSaveDialog(transactionList.getScene().getWindow());
            if (selected == null) return;

            File output = selected.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")
                    ? selected
                    : (selected.getParentFile() == null
                    ? new File(selected.getName() + ".pdf")
                    : new File(selected.getParentFile(), selected.getName() + ".pdf"));

            boolean includeId = !SessionContext.isClient();
            File out = pdfExportService.exportTransactions(rows, output, includeId);
            ensureValidPdf(out);

            Alert ok = new Alert(Alert.AlertType.INFORMATION, "Fichier gÃ©nÃ©rÃ©:\n" + out.getAbsolutePath(), ButtonType.OK);
            ok.setHeaderText("Export PDF terminÃ©");
            ok.showAndWait();
        } catch (Exception ex) {
            showError("Export PDF impossible: " + ex.getMessage());
        }
    }

    private void ensureValidPdf(File file) {
        if (file == null || !file.exists() || file.length() < 10) {
            throw new IllegalStateException("PDF invalide (fichier vide ou absent).");
        }
        byte[] head = new byte[5];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int read = in.read(head);
            String sig = read <= 0 ? "" : new String(head, 0, read, java.nio.charset.StandardCharsets.US_ASCII);
            if (!sig.startsWith("%PDF-")) {
                throw new IllegalStateException("PDF invalide (signature manquante).");
            }
        } catch (Exception e) {
            throw new IllegalStateException("PDF invalide: " + e.getMessage(), e);
        }
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



