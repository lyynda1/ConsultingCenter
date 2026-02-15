package com.advisora.GUI.Investissement;

import com.advisora.GUI.MainController;
import com.advisora.Model.Investment;
import com.advisora.Services.InvestmentService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.Time;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class InvestmentListController {

    @FXML private ListView<Investment> investmentList;
    @FXML private TextField txtSearch;
    @FXML private Label countLabel;
    @FXML private Label formTitleLabel;
    @FXML private Button submitFormBtn;
    @FXML private Label leftStatus;
    @FXML private Label rightStatus;
    @FXML private TextField idField;
    @FXML private javafx.scene.control.TextArea commentaireField;
    @FXML private TextField dureeField;
    @FXML private TextField budMinField;
    @FXML private TextField budMaxField;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private TextField idProjField;
    @FXML private TextField idUserField;
    @FXML private HBox listPane;
    @FXML private VBox formPane;

    private Investment editingInvestment = null;
    private MainController mainController;
    private final InvestmentService investmentService = new InvestmentService();
    private final ObservableList<Investment> allObs = FXCollections.observableArrayList();
    private final ObservableList<Investment> viewObs = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        currencyCombo.getItems().setAll("TND", "EUR", "USD", "GBP");
        currencyCombo.getSelectionModel().select("TND");

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
                String comment = safe(inv.getCommentaireInv());
                if (comment.length() > 60) comment = comment.substring(0, 57) + "...";
                String line = String.format("#%d | %s | %s %.0f - %.0f",
                        inv.getIdInv(), comment, safe(inv.getCurrencyInv()), inv.getBud_minInv(), inv.getBud_maxInv());
                setText(line);
                setGraphic(null);
            }
        });

        investmentList.getSelectionModel().selectedItemProperty().addListener((o, old, inv) -> {
            if (inv != null) {
                initForEdit(inv);
                idField.setText(String.valueOf(inv.getIdInv()));
                if (rightStatus != null) rightStatus.setText("Sélection: #" + inv.getIdInv());
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
    private void openTransactions(javafx.event.ActionEvent e) {
        if (mainController != null) {
            mainController.showTransactions();
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
        if (leftStatus != null) leftStatus.setText("Remplissez le formulaire pour ajouter un investissement.");
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
        String commentaire = commentaireField == null ? "" : commentaireField.getText();
        String dureeStr = dureeField == null ? "00:00:00" : dureeField.getText().trim();
        if (dureeStr.isEmpty()) dureeStr = "00:00:00";
        Time duree;
        try {
            duree = Time.valueOf(dureeStr);
        } catch (Exception ex) {
            if (leftStatus != null) leftStatus.setText("Durée invalide. Utilisez HH:mm:ss");
            return;
        }
        double budMin, budMax;
        try {
            budMin = Double.parseDouble(budMinField.getText().trim());
            budMax = Double.parseDouble(budMaxField.getText().trim());
        } catch (NumberFormatException ex) {
            if (leftStatus != null) leftStatus.setText("Budget min et max doivent être des nombres.");
            return;
        }
        String currency = currencyCombo.getValue() == null ? "TND" : currencyCombo.getValue();
        int idProj, idUser;
        try {
            idProj = Integer.parseInt(idProjField.getText().trim());
            idUser = Integer.parseInt(idUserField.getText().trim());
        } catch (NumberFormatException ex) {
            if (leftStatus != null) leftStatus.setText("ID Projet et ID Utilisateur doivent être des entiers.");
            return;
        }

        try {
            if (editingInvestment != null) {
                editingInvestment.setCommentaireInv(commentaire);
                editingInvestment.setDureeInv(duree);
                editingInvestment.setBud_minInv(budMin);
                editingInvestment.setBud_maxInv(budMax);
                editingInvestment.setCurrencyInv(currency);
                editingInvestment.setIdProj(idProj);
                editingInvestment.setIdUser(idUser);
                investmentService.modifier(editingInvestment);
                if (leftStatus != null) leftStatus.setText("Investissement #" + editingInvestment.getIdInv() + " modifié.");
            } else {
                Investment inv = new Investment(commentaire, duree, budMin, budMax, currency, idProj, idUser);
                investmentService.ajouter(inv);
                if (leftStatus != null) leftStatus.setText("Investissement ajouté.");
            }
            refresh();
            initForAdd();
            showListWidget();
        } catch (Exception ex) {
            if (leftStatus != null) leftStatus.setText("Erreur: " + ex.getMessage());
        }
    }

    private void showListWidget() {
        if (listPane != null) listPane.setVisible(true);
        if (listPane != null) listPane.setManaged(true);
        if (formPane != null) { formPane.setVisible(false); formPane.setManaged(false); }
    }

    private void showFormWidget() {
        if (listPane != null) { listPane.setVisible(false); listPane.setManaged(false); }
        if (formPane != null) { formPane.setVisible(true); formPane.setManaged(true); formPane.toFront(); }
    }

    @FXML
    private void handleEditById(javafx.event.ActionEvent e) {
        Investment inv = getInvestmentFromIdOrSelection();
        if (inv == null) {
            if (rightStatus != null) rightStatus.setText("Saisir un ID ou sélectionner une ligne.");
            return;
        }
        initForEdit(inv);
        idField.setText(String.valueOf(inv.getIdInv()));
        showFormWidget();
        if (leftStatus != null) leftStatus.setText("Modification: #" + inv.getIdInv());
    }

    @FXML
    private void handleDeleteById(javafx.event.ActionEvent e) {
        Investment inv = getInvestmentFromIdOrSelection();
        if (inv == null) {
            if (rightStatus != null) rightStatus.setText("Saisir un ID ou sélectionner une ligne.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Supprimer l'investissement #" + inv.getIdInv() + " ?");
        confirm.setContentText("Cette action est irréversible.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            investmentService.supprimer(inv);
            refresh();
            initForAdd();
            idField.clear();
            if (rightStatus != null) rightStatus.setText("Investissement supprimé.");
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Suppression échouée: " + ex.getMessage()).showAndWait();
        }
    }

    private Investment getInvestmentFromIdOrSelection() {
        Investment sel = investmentList.getSelectionModel().getSelectedItem();
        if (sel != null) return sel;
        String sid = idField == null ? "" : idField.getText().trim();
        if (sid.isEmpty()) return null;
        try {
            int id = Integer.parseInt(sid);
            return investmentService.getInvestmentById(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void initForAdd() {
        editingInvestment = null;
        if (formTitleLabel != null) formTitleLabel.setText("Formulaire - Ajout");
        if (submitFormBtn != null) submitFormBtn.setText("Ajouter");
        clearFormFields();
    }

    private void initForEdit(Investment inv) {
        editingInvestment = inv;
        if (formTitleLabel != null) formTitleLabel.setText("Formulaire - Modifier");
        if (submitFormBtn != null) submitFormBtn.setText("Modifier");
        if (inv == null) {
            clearFormFields();
            return;
        }
        commentaireField.setText(inv.getCommentaireInv() == null ? "" : inv.getCommentaireInv());
        dureeField.setText(inv.getDureeInv() == null ? "" : inv.getDureeInv().toString());
        budMinField.setText(String.valueOf(inv.getBud_minInv()));
        budMaxField.setText(String.valueOf(inv.getBud_maxInv()));
        if (inv.getCurrencyInv() != null && currencyCombo.getItems().contains(inv.getCurrencyInv()))
            currencyCombo.getSelectionModel().select(inv.getCurrencyInv());
        else
            currencyCombo.getSelectionModel().select("TND");
        idProjField.setText(String.valueOf(inv.getIdProj()));
        idUserField.setText(String.valueOf(inv.getIdUser()));
    }

    private void clearFormFields() {
        if (commentaireField != null) commentaireField.clear();
        if (dureeField != null) dureeField.clear();
        if (budMinField != null) budMinField.clear();
        if (budMaxField != null) budMaxField.clear();
        if (currencyCombo != null) currencyCombo.getSelectionModel().select("TND");
        if (idProjField != null) idProjField.clear();
        if (idUserField != null) idUserField.clear();
    }

    @FXML
    private void onSearch(KeyEvent e) {
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
    }

    private void refresh() {
        List<Investment> list = investmentService.afficher();
        allObs.setAll(list);
        applyFilter(txtSearch == null ? "" : txtSearch.getText());
        if (countLabel != null) countLabel.setText(viewObs.size() + " investissements");
    }

    private void applyFilter(String q) {
        String s = (q == null) ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (s.isBlank()) {
            viewObs.setAll(allObs);
        } else {
            viewObs.setAll(allObs.stream().filter(inv ->
                    safe(inv.getCommentaireInv()).toLowerCase(Locale.ROOT).contains(s)
                            || safe(inv.getCurrencyInv()).toLowerCase(Locale.ROOT).contains(s)
                            || String.valueOf(inv.getIdProj()).contains(s)
                            || String.valueOf(inv.getIdUser()).contains(s)
                            || String.valueOf(inv.getIdInv()).contains(s)
            ).toList());
        }
        if (countLabel != null) countLabel.setText(viewObs.size() + " investissements");
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
