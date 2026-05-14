package com.advisora.GUI.Investissement;

import com.advisora.Model.investment.Investment;
import com.advisora.Model.projet.Project;
import com.advisora.Model.user.User;
import com.advisora.Services.investment.InvestmentService;
import com.advisora.Services.projet.ProjectService;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.user.UserService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.Time;
import java.util.List;
import java.util.stream.Collectors;

public class AddInvestissementDialogController {

    @FXML private Label dialogTitle;
    @FXML private Button submitButton;
    @FXML private TextArea commentaireField;
    @FXML private TextField dureeField;
    @FXML private ComboBox<String> dureeUnitCombo;
    @FXML private TextField budMinField;
    @FXML private TextField budMaxField;
    @FXML private ComboBox<String> currencyCombo;

    @FXML private ComboBox<ProjectOption> projectCombo;
    @FXML private ComboBox<UserOption> userCombo;
    @FXML private HBox adminProjectRow;

    @FXML private VBox clientProjectRow;
    @FXML private ComboBox<String> projectNameCombo;
    @FXML private HBox dragHandle;

    private Investment editingInvestment = null;
    private Runnable onClose = () -> {};
    private Runnable onSaved = () -> {};
    private final InvestmentService service = new InvestmentService();
    private final ProjectService projectService = new ProjectService();
    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        currencyCombo.getItems().setAll("TND", "EUR", "USD", "GBP");
        currencyCombo.getSelectionModel().select("TND");

        if (dureeUnitCombo != null) {
            dureeUnitCombo.getItems().setAll("Heures", "Jours", "Semaines", "Mois", "Annees");
            dureeUnitCombo.getSelectionModel().select("Heures");
        }

        loadAdminCombos();
        applyRoleVisibility();
    }

    private void loadAdminCombos() {
        if (projectCombo != null) {
            List<ProjectOption> projectOptions = projectService.getAll().stream()
                    .map(p -> new ProjectOption(
                            p.getIdProj(),
                            "#" + p.getIdProj() + " - " + safe(p.getTitleProj(), "Projet sans titre")))
                    .collect(Collectors.toList());
            projectCombo.getItems().setAll(projectOptions);
        }

        if (userCombo != null) {
            List<UserOption> userOptions = userService.afficher().stream()
                    .map(u -> new UserOption(u.getId(), formatUserLabel(u)))
                    .collect(Collectors.toList());
            userCombo.getItems().setAll(userOptions);
        }
    }

    private void applyRoleVisibility() {
        boolean isClient = SessionContext.isClient();

        if (adminProjectRow != null) {
            adminProjectRow.setVisible(!isClient);
            adminProjectRow.setManaged(!isClient);
        }
        if (clientProjectRow != null) {
            clientProjectRow.setVisible(isClient);
            clientProjectRow.setManaged(isClient);
        }

        if (isClient && projectNameCombo != null) {
            List<String> titles = projectService.getAll().stream()
                    .map(Project::getTitleProj)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.toList());
            projectNameCombo.getItems().setAll(titles);
        } else {
            selectCurrentUserByDefault();
        }
    }

    private void selectCurrentUserByDefault() {
        if (userCombo == null) return;
        int currentUserId = SessionContext.getCurrentUserId();
        userCombo.getItems().stream()
                .filter(u -> u.id() == currentUserId)
                .findFirst()
                .ifPresent(u -> userCombo.getSelectionModel().select(u));
    }

    public Node getDragHandle() {
        return dragHandle;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void initForAdd() {
        editingInvestment = null;
        if (dialogTitle != null) dialogTitle.setText("Nouvel Investissement");
        if (submitButton != null) submitButton.setText("Ajouter");
        clearFields();
    }

    public void initForEdit(Investment inv) {
        editingInvestment = inv;
        if (dialogTitle != null) dialogTitle.setText("Modifier l'investissement");
        if (submitButton != null) submitButton.setText("Modifier");

        if (inv == null) {
            clearFields();
            return;
        }

        commentaireField.setText(inv.getCommentaireInv() == null ? "" : inv.getCommentaireInv());
        if (inv.getDureeInv() != null) {
            String[] parts = inv.getDureeInv().toString().split(":");
            dureeField.setText(parts.length > 0 ? parts[0] : "");
        } else {
            dureeField.clear();
        }

        if (dureeUnitCombo != null) {
            dureeUnitCombo.getSelectionModel().select("Heures");
        }

        budMinField.setText(String.valueOf(inv.getBud_minInv()));
        budMaxField.setText(String.valueOf(inv.getBud_maxInv()));

        if (inv.getCurrencyInv() != null && currencyCombo.getItems().contains(inv.getCurrencyInv())) {
            currencyCombo.getSelectionModel().select(inv.getCurrencyInv());
        } else {
            currencyCombo.getSelectionModel().select("TND");
        }

        if (SessionContext.isClient()) {
            Project p = projectService.getById(inv.getIdProj());
            if (p != null && p.getTitleProj() != null && projectNameCombo.getItems().contains(p.getTitleProj())) {
                projectNameCombo.getSelectionModel().select(p.getTitleProj());
            }
        } else {
            if (projectCombo != null) {
                projectCombo.getItems().stream()
                        .filter(p -> p.id() == inv.getIdProj())
                        .findFirst()
                        .ifPresent(p -> projectCombo.getSelectionModel().select(p));
            }

            if (userCombo != null) {
                userCombo.getItems().stream()
                        .filter(u -> u.id() == inv.getIdUser())
                        .findFirst()
                        .ifPresent(u -> userCombo.getSelectionModel().select(u));
            }
        }
    }

    private void clearFields() {
        if (commentaireField != null) commentaireField.clear();
        if (dureeField != null) dureeField.clear();
        if (dureeUnitCombo != null) dureeUnitCombo.getSelectionModel().select("Heures");
        if (budMinField != null) budMinField.clear();
        if (budMaxField != null) budMaxField.clear();
        if (currencyCombo != null) currencyCombo.getSelectionModel().select("TND");
        if (projectCombo != null) projectCombo.getSelectionModel().clearSelection();
        if (userCombo != null) userCombo.getSelectionModel().clearSelection();
        if (projectNameCombo != null) projectNameCombo.getSelectionModel().clearSelection();

        if (!SessionContext.isClient()) {
            selectCurrentUserByDefault();
        }
    }

    @FXML
    private void close() {
        onClose.run();
    }

    @FXML
    private void save() {
        String commentaire;
        try {
            commentaire = required(commentaireField.getText().trim(), "Le commentaire est requis");
        } catch (IllegalArgumentException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        double dureeValue;
        try {
            String txt = dureeField == null ? "" : dureeField.getText().trim();
            if (txt.isEmpty()) {
                throw new NumberFormatException();
            }
            dureeValue = Double.parseDouble(txt);
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Duree invalide. Saisissez un nombre.").showAndWait();
            return;
        }

        if (dureeValue <= 0) {
            new Alert(Alert.AlertType.ERROR, "La duree doit etre positive.").showAndWait();
            return;
        }

        String unit = (dureeUnitCombo == null || dureeUnitCombo.getValue() == null)
                ? "Heures"
                : dureeUnitCombo.getValue();
        double hours;
        switch (unit) {
            case "Jours" -> hours = dureeValue * 24;
            case "Semaines" -> hours = dureeValue * 7 * 24;
            case "Mois" -> hours = dureeValue * 30 * 24;
            case "Annees" -> hours = dureeValue * 365 * 24;
            default -> hours = dureeValue;
        }

        int h = (int) Math.max(0, Math.min(23, Math.round(hours)));
        Time duree = Time.valueOf(String.format("%02d:00:00", h));

        double budMin;
        double budMax;
        try {
            budMin = Double.parseDouble(budMinField.getText().trim());
            budMax = Double.parseDouble(budMaxField.getText().trim());
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Budget min et max doivent etre des nombres.").showAndWait();
            return;
        }

        if (budMin < 0 || budMax < 0) {
            new Alert(Alert.AlertType.ERROR, "Les budgets ne peuvent pas etre negatifs.").showAndWait();
            return;
        }
        if (budMin > budMax) {
            new Alert(Alert.AlertType.ERROR, "Le budget min ne peut pas depasser le budget max.").showAndWait();
            return;
        }

        String currency = currencyCombo.getValue() == null ? "TND" : currencyCombo.getValue();

        int idProj;
        int idUser;
        if (SessionContext.isClient()) {
            String projectName = projectNameCombo.getValue() == null ? "" : projectNameCombo.getValue().trim();
            if (projectName.isEmpty()) {
                new Alert(Alert.AlertType.ERROR, "Veuillez choisir un projet existant.").showAndWait();
                return;
            }
            Project p = projectService.getByTitle(projectName);
            if (p == null) {
                new Alert(Alert.AlertType.ERROR, "Projet introuvable: " + projectName).showAndWait();
                return;
            }
            idProj = p.getIdProj();
            idUser = SessionContext.getCurrentUserId();
        } else {
            ProjectOption selectedProject = projectCombo == null ? null : projectCombo.getValue();
            UserOption selectedUser = userCombo == null ? null : userCombo.getValue();

            if (selectedProject == null) {
                new Alert(Alert.AlertType.ERROR, "Veuillez choisir un projet.").showAndWait();
                return;
            }
            if (selectedUser == null) {
                new Alert(Alert.AlertType.ERROR, "Veuillez choisir un utilisateur.").showAndWait();
                return;
            }

            idProj = selectedProject.id();
            idUser = selectedUser.id();
        }

        if (editingInvestment != null) {
            editingInvestment.setCommentaireInv(commentaire);
            editingInvestment.setDureeInv(duree);
            editingInvestment.setBud_minInv(budMin);
            editingInvestment.setBud_maxInv(budMax);
            editingInvestment.setCurrencyInv(currency);
            editingInvestment.setIdProj(idProj);
            editingInvestment.setIdUser(idUser);
            service.modifier(editingInvestment);
        } else {
            Investment inv = new Investment(commentaire, duree, budMin, budMax, currency, idProj, idUser);
            service.ajouter(inv);
        }

        onSaved.run();
    }

    private String required(String trim, String errorMessage) {
        if (trim == null || trim.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return trim;
    }

    private String formatUserLabel(User u) {
        String fullName = ((u.getPrenom() == null ? "" : u.getPrenom().trim()) + " " +
                (u.getNom() == null ? "" : u.getNom().trim())).trim();
        String email = safe(u.getEmail(), "email inconnu");
        if (!fullName.isBlank()) {
            return "#" + u.getId() + " - " + fullName + " (" + email + ")";
        }
        return "#" + u.getId() + " - " + email;
    }

    private String safe(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? fallback : v;
    }

    private record ProjectOption(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record UserOption(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
