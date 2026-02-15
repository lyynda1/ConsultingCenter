package com.advisora.GUI.Transaction;

import com.advisora.GUI.MainController;
import com.advisora.Model.Transaction;
import com.advisora.Services.TransactionService;
import com.advisora.enums.transactionStatut;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.Date;
import java.util.List;
import java.util.Locale;

public class TransactionListController {

    @FXML private ListView<Transaction> transactionList;
    @FXML private TextField txtSearch;
    @FXML private Label countLabel;
    @FXML private Label formTitleLabel;
    @FXML private Button submitFormBtn;
    @FXML private Label leftStatus;
    @FXML private Label rightStatus;
    @FXML private TextField idField;
    @FXML private TextField dateField;
    @FXML private TextField montantField;
    @FXML private TextField typeField;
    @FXML private ComboBox<transactionStatut> statutCombo;
    @FXML private TextField idInvField;
    @FXML private HBox listPane;
    @FXML private VBox formPane;

    private Transaction editingTransaction = null;
    private MainController mainController;
    private final TransactionService transactionService = new TransactionService();
    private final ObservableList<Transaction> allObs = FXCollections.observableArrayList();
    private final ObservableList<Transaction> viewObs = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        statutCombo.getItems().setAll(transactionStatut.values());
        statutCombo.getSelectionModel().select(transactionStatut.PENDING);

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
                String dateStr = t.getDateTransac() == null ? "-" : t.getDateTransac().toString();
                String line = String.format("#%d | %s | %.2f | %s | Inv#%d",
                        t.getIdTransac(), dateStr, t.getMontantTransac(), safe(t.getType()), t.getIdInv());
                setText(line);
                setGraphic(null);
            }
        });

        transactionList.getSelectionModel().selectedItemProperty().addListener((o, old, t) -> {
            if (t != null) {
                initForEdit(t);
                idField.setText(String.valueOf(t.getIdTransac()));
                if (rightStatus != null) rightStatus.setText("Sélection: #" + t.getIdTransac());
            }
        });

        refresh();
        initForAdd();
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldV, q) -> applyFilter(q));
        }
    }

    /** Appelé par MainController pour changer de vue dans la même fenêtre (plus de rectangle gris). */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void openInvestissements(javafx.event.ActionEvent e) {
        if (mainController != null) {
            mainController.showInvestissements();
        }
    }

    @FXML
    private void handleRefresh(javafx.event.ActionEvent e) {
        refresh();
        if (leftStatus != null) leftStatus.setText("Liste actualisée.");
    }

    /** Ouvre le formulaire en mode Ajout (affiche le widget formulaire). */
    @FXML
    private void handleShowAddForm(javafx.event.ActionEvent e) {
        initForAdd();
        showFormWidget();
        if (leftStatus != null) leftStatus.setText("Remplissez le formulaire pour ajouter une transaction.");
    }

    /** Retour à la liste (cache le formulaire). */
    @FXML
    private void handleBackToList(javafx.event.ActionEvent e) {
        showListWidget();
    }

    @FXML
    private void handleClearForm(javafx.event.ActionEvent e) {
        initForAdd();
        if (leftStatus != null) leftStatus.setText("Formulaire réinitialisé.");
    }

    @FXML
    private void handleSubmitForm(javafx.event.ActionEvent e) {
        Date date;
        try {
            date = Date.valueOf(dateField.getText().trim());
        } catch (Exception ex) {
            if (leftStatus != null) leftStatus.setText("Date invalide. Utilisez yyyy-MM-dd");
            return;
        }
        double montant;
        try {
            montant = Double.parseDouble(montantField.getText().trim());
        } catch (NumberFormatException ex) {
            if (leftStatus != null) leftStatus.setText("Montant doit être un nombre.");
            return;
        }
        String type = typeField.getText() == null ? "" : typeField.getText().trim();
        if (type.isEmpty()) {
            if (leftStatus != null) leftStatus.setText("Veuillez saisir le type.");
            return;
        }
        transactionStatut statut = statutCombo.getValue() == null ? transactionStatut.PENDING : statutCombo.getValue();
        int idInv;
        try {
            idInv = Integer.parseInt(idInvField.getText().trim());
        } catch (NumberFormatException ex) {
            if (leftStatus != null) leftStatus.setText("ID Investissement doit être un entier.");
            return;
        }

        try {
            if (editingTransaction != null) {
                editingTransaction.setDateTransac(date);
                editingTransaction.setMontantTransac(montant);
                editingTransaction.setType(type);
                editingTransaction.setStatut(statut);
                editingTransaction.setIdInv(idInv);
                transactionService.modifier(editingTransaction);
                if (leftStatus != null) leftStatus.setText("Transaction #" + editingTransaction.getIdTransac() + " modifiée.");
            } else {
                Transaction t = new Transaction(date, montant, type, statut, idInv);
                transactionService.ajouter(t);
                if (leftStatus != null) leftStatus.setText("Transaction ajoutée.");
            }
            refresh();
            initForAdd();
            showListWidget();
        } catch (Exception ex) {
            if (leftStatus != null) leftStatus.setText("Erreur: " + ex.getMessage());
        }
    }

    @FXML
    private void handleEditById(javafx.event.ActionEvent e) {
        Transaction t = getTransactionFromIdOrSelection();
        if (t == null) {
            if (rightStatus != null) rightStatus.setText("Saisir un ID ou sélectionner une ligne.");
            return;
        }
        initForEdit(t);
        idField.setText(String.valueOf(t.getIdTransac()));
        showFormWidget();
        if (rightStatus != null) rightStatus.setText("Modification: #" + t.getIdTransac());
    }

    @FXML
    private void handleDeleteById(javafx.event.ActionEvent e) {
        Transaction t = getTransactionFromIdOrSelection();
        if (t == null) {
            if (rightStatus != null) rightStatus.setText("Saisir un ID ou sélectionner une ligne.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer la transaction #" + t.getIdTransac() + " ?");
        confirm.setContentText("Cette action est irréversible.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            transactionService.supprimer(t);
            refresh();
            initForAdd();
            idField.clear();
            if (rightStatus != null) rightStatus.setText("Transaction supprimée.");
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Suppression échouée: " + ex.getMessage()).showAndWait();
        }
    }

    private void showListWidget() {
        if (listPane != null) { listPane.setVisible(true); listPane.setManaged(true); }
        if (formPane != null) { formPane.setVisible(false); formPane.setManaged(false); }
    }

    private void showFormWidget() {
        if (listPane != null) { listPane.setVisible(false); listPane.setManaged(false); }
        if (formPane != null) { formPane.setVisible(true); formPane.setManaged(true); formPane.toFront(); }
    }

    private Transaction getTransactionFromIdOrSelection() {
        Transaction sel = transactionList.getSelectionModel().getSelectedItem();
        if (sel != null) return sel;
        String sid = idField == null ? "" : idField.getText().trim();
        if (sid.isEmpty()) return null;
        try {
            int id = Integer.parseInt(sid);
            return transactionService.getTransactionById(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void initForAdd() {
        editingTransaction = null;
        if (formTitleLabel != null) formTitleLabel.setText("Formulaire - Ajout");
        if (submitFormBtn != null) submitFormBtn.setText("Ajouter");
        clearFormFields();
    }

    private void initForEdit(Transaction t) {
        editingTransaction = t;
        if (formTitleLabel != null) formTitleLabel.setText("Formulaire - Modifier");
        if (submitFormBtn != null) submitFormBtn.setText("Modifier");
        if (t == null) {
            clearFormFields();
            return;
        }
        dateField.setText(t.getDateTransac() == null ? "" : t.getDateTransac().toString());
        montantField.setText(String.valueOf(t.getMontantTransac()));
        typeField.setText(t.getType() == null ? "" : t.getType());
        if (t.getStatut() != null) statutCombo.getSelectionModel().select(t.getStatut());
        idInvField.setText(String.valueOf(t.getIdInv()));
    }

    private void clearFormFields() {
        if (dateField != null) dateField.clear();
        if (montantField != null) montantField.clear();
        if (typeField != null) typeField.clear();
        if (statutCombo != null) statutCombo.getSelectionModel().select(transactionStatut.PENDING);
        if (idInvField != null) idInvField.clear();
    }

    @FXML
    private void onSearch(KeyEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    private void refresh() {
        List<Transaction> list = transactionService.afficher();
        allObs.setAll(list);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
        if (countLabel != null) countLabel.setText(viewObs.size() + " transactions");
    }

    private void applyFilter(String q) {
        String s = (q == null) ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (s.isBlank()) {
            viewObs.setAll(allObs);
        } else {
            viewObs.setAll(allObs.stream().filter(t ->
                    safe(t.getType()).toLowerCase(Locale.ROOT).contains(s)
                            || (t.getStatut() != null && t.getStatut().name().toLowerCase(Locale.ROOT).contains(s))
                            || String.valueOf(t.getIdInv()).contains(s)
                            || String.valueOf(t.getMontantTransac()).contains(s)
                            || (t.getDateTransac() != null && t.getDateTransac().toString().contains(s))
                            || String.valueOf(t.getIdTransac()).contains(s)
            ).toList());
        }
        if (countLabel != null) countLabel.setText(viewObs.size() + " transactions");
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
