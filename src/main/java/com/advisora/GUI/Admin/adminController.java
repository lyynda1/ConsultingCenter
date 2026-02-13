package com.advisora.GUI.Admin;

import com.advisora.Model.User;
import com.advisora.Services.UserService;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

public class adminController {

    @FXML private TextField nomField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private TextField cinField;
    @FXML private TextField telField;
    @FXML private TextField expertiseField;
    @FXML private TextField dateNField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<UserRole> roleCombo;

    // ✅ error labels under fields (must exist in FXML)
    @FXML private Label nomError;
    @FXML private Label prenomError;
    @FXML private Label emailError;
    @FXML private Label cinError;
    @FXML private Label telError;
    @FXML private Label dateNError;
    @FXML private Label passwordError;
    @FXML private Label roleError;
    @FXML private Label expertiseError;

    @FXML private ListView<User> usersList;
    @FXML private Label countLabel;
    @FXML private Label leftStatus;
    @FXML private Label rightStatus;

    @FXML private TextField idField;
    @FXML private TextField searchField;

    private final UserService userService = new UserService();
    private final ObservableList<User> usersObs = FXCollections.observableArrayList();

    // --------------------------
    // VALIDATION RULES
    // --------------------------
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[\\p{L}][\\p{L} \\-']{1,49}$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern CIN_PATTERN =
            Pattern.compile("^\\d{8}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?\\d{8,15}$");

    private static final DateTimeFormatter DOB_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    public void initialize() {
        roleCombo.setItems(FXCollections.observableArrayList(UserRole.values()));
        usersList.setItems(usersObs);

        usersList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null :
                        "#" + u.getId() + " • " + u.getNom() + " " + u.getPrenom()
                                + " • " + u.getEmail() + " • " + u.getRole());
            }
        });

        usersList.getSelectionModel().selectedItemProperty().addListener((obs, oldU, u) -> {
            if (u != null) {
                fillForm(u);
                idField.setText(String.valueOf(u.getId()));
                rightStatus.setText("Selected user #" + u.getId());
                clearErrors(); // ✅
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldV, q) -> applyFilter(q));
        }

        // ✅ Expertise only if GERANT
        roleCombo.valueProperty().addListener((obs, oldR, newR) -> {
            boolean isGerant = (newR == UserRole.GERANT);
            expertiseField.setDisable(!isGerant);
            if (!isGerant) expertiseField.clear();

            // clear error if role changed
            hideError(roleCombo, roleError);
            if (!isGerant) hideError(expertiseField, expertiseError);
        });

        expertiseField.setDisable(true);
        refreshList();

        // Optional: validate live as user types
        addLiveValidation();
    }

    // --------------------------
    // Handlers
    // --------------------------

    @FXML
    private void handleAddUser() {
        clearErrors();
        User u = buildUserFromForm(0);
        if (!validateForm()) {
            leftStatus.setText("Corrige les champs en rouge ❌");
            return;
        }

        userService.ajouter(u);
        leftStatus.setText("User added ✅ ID=" + u.getId());
        clearForm();
        refreshList();
    }

    @FXML
    private void handleModifyById(MouseEvent e) {
        clearErrors();
        int id = parseIdOrThrow();
        User u = buildUserFromForm(id);

        if (!validateForm()) {
            leftStatus.setText("Corrige les champs en rouge ❌");
            return;
        }

        userService.modifier(u);
        leftStatus.setText("User updated ✅ ID=" + id);
        refreshList();
    }

    @FXML
    private void handleDeleteById(MouseEvent e) {
        try {
            int id = parseIdOrThrow();
            userService.supprimerParId(id);
            leftStatus.setText("User deleted ✅ ID=" + id);
            clearForm();
            idField.clear();
            refreshList();
        } catch (Exception ex) {
            leftStatus.setText("Delete failed ❌ " + ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        refreshList();
        leftStatus.setText("Refreshed ✅");
    }

    @FXML
    private void handleClear() {
        clearForm();
        clearErrors();
        idField.clear();
        usersList.getSelectionModel().clearSelection();
        leftStatus.setText("Cleared ✅");
    }

    // --------------------------
    // Validation UI (no popups)
    // --------------------------

    private boolean validateForm() {
        boolean ok = true;

        String nom = safe(nomField.getText());
        String prenom = safe(prenomField.getText());
        String email = safe(emailField.getText());
        String cin = safe(cinField.getText());
        String tel = safe(telField.getText());
        String dobTxt = safe(dateNField.getText());
        String pass = passwordField.getText() == null ? "" : passwordField.getText();
        UserRole role = roleCombo.getValue();
        String expertise = safe(expertiseField.getText());

        // NOM
        if (nom.isBlank()) ok &= showError(nomField, nomError, "Nom obligatoire");
        else if (!NAME_PATTERN.matcher(nom).matches()) ok &= showError(nomField, nomError, "Nom invalide (lettres seulement)");
        else hideError(nomField, nomError);

        // PRENOM
        if (prenom.isBlank()) ok &= showError(prenomField, prenomError, "Prénom obligatoire");
        else if (!NAME_PATTERN.matcher(prenom).matches()) ok &= showError(prenomField, prenomError, "Prénom invalide (lettres seulement)");
        else hideError(prenomField, prenomError);

        // EMAIL
        if (email.isBlank()) ok &= showError(emailField, emailError, "Email obligatoire");
        else if (!EMAIL_PATTERN.matcher(email).matches()) ok &= showError(emailField, emailError, "Email invalide");
        else hideError(emailField, emailError);

        // CIN
        if (cin.isBlank()) ok &= showError(cinField, cinError, "CIN obligatoire");
        else if (!CIN_PATTERN.matcher(cin).matches()) ok &= showError(cinField, cinError, "CIN invalide (8 chiffres)");
        else hideError(cinField, cinError);

        // TEL
        if (tel.isBlank()) ok &= showError(telField, telError, "Téléphone obligatoire");
        else if (!PHONE_PATTERN.matcher(tel).matches()) ok &= showError(telField, telError, "Téléphone invalide (8-15 chiffres)");
        else hideError(telField, telError);

        // DATE
        if (dobTxt.isBlank()) {
            ok &= showError(dateNField, dateNError, "Date obligatoire (YYYY-MM-DD)");
        } else {
            try {
                LocalDate dob = LocalDate.parse(dobTxt, DOB_FMT);
                if (dob.isAfter(LocalDate.now())) ok &= showError(dateNField, dateNError, "Date invalide (dans le futur)");
                else {
                    int age = Period.between(dob, LocalDate.now()).getYears();
                    if (age < 10) ok &= showError(dateNField, dateNError, "Âge invalide (<10 ans)");
                    else hideError(dateNField, dateNError);
                }
            } catch (DateTimeParseException ex) {
                ok &= showError(dateNField, dateNError, "Format date: YYYY-MM-DD");
            }
        }

        // PASSWORD
        if (pass.isBlank()) ok &= showError(passwordField, passwordError, "Mot de passe obligatoire");
        else hideError(passwordField, passwordError);

        // ROLE
        if (role == null) ok &= showError(roleCombo, roleError, "Choisir un rôle");
        else hideError(roleCombo, roleError);

        // EXPERTISE
        if (role == UserRole.GERANT) {
            if (expertise.isBlank()) ok &= showError(expertiseField, expertiseError, "Expertise obligatoire pour GERANT");
            else hideError(expertiseField, expertiseError);
        } else {
            hideError(expertiseField, expertiseError);
        }

        return ok;
    }

    private boolean showError(Control field, Label errLabel, String msg) {
        if (errLabel != null) {
            errLabel.setText(msg);
            errLabel.setManaged(true);
            errLabel.setVisible(true);
        }
        if (field != null && !field.getStyleClass().contains("input-error")) {
            field.getStyleClass().add("input-error");
        }
        return false;
    }

    private void hideError(Control field, Label errLabel) {
        if (errLabel != null) {
            errLabel.setText("");
            errLabel.setVisible(false);
            errLabel.setManaged(false);
        }
        if (field != null) {
            field.getStyleClass().remove("input-error");
        }
    }

    private void clearErrors() {
        hideError(nomField, nomError);
        hideError(prenomField, prenomError);
        hideError(emailField, emailError);
        hideError(cinField, cinError);
        hideError(telField, telError);
        hideError(dateNField, dateNError);
        hideError(passwordField, passwordError);
        hideError(roleCombo, roleError);
        hideError(expertiseField, expertiseError);
    }

    private void addLiveValidation() {
        nomField.textProperty().addListener((o,a,b) -> validateForm());
        prenomField.textProperty().addListener((o,a,b) -> validateForm());
        emailField.textProperty().addListener((o,a,b) -> validateForm());
        cinField.textProperty().addListener((o,a,b) -> validateForm());
        telField.textProperty().addListener((o,a,b) -> validateForm());
        dateNField.textProperty().addListener((o,a,b) -> validateForm());
        passwordField.textProperty().addListener((o,a,b) -> validateForm());
        roleCombo.valueProperty().addListener((o,a,b) -> validateForm());
        expertiseField.textProperty().addListener((o,a,b) -> validateForm());
    }

    // --------------------------
    // CRUD helpers
    // --------------------------

    private void refreshList() {
        List<User> all = userService.afficher();
        usersObs.setAll(all);
        countLabel.setText(all.size() + " users");
    }

    private void applyFilter(String q) {
        if (q == null || q.isBlank()) {
            refreshList();
            return;
        }
        String s = q.trim().toLowerCase();
        List<User> all = userService.afficher();
        usersObs.setAll(all.stream().filter(u ->
                (u.getNom() != null && u.getNom().toLowerCase().contains(s)) ||
                        (u.getPrenom() != null && u.getPrenom().toLowerCase().contains(s)) ||
                        (u.getEmail() != null && u.getEmail().toLowerCase().contains(s)) ||
                        (u.getRole() != null && u.getRole().name().toLowerCase().contains(s))
        ).toList());
        countLabel.setText(usersObs.size() + " users");
    }

    private int parseIdOrThrow() {
        String txt = idField.getText();
        if (txt == null || txt.isBlank()) throw new IllegalArgumentException("Enter User ID");
        return Integer.parseInt(txt.trim());
    }

    private User buildUserFromForm(int id) {
        UserRole role = roleCombo.getValue();
        String expertise = safe(expertiseField.getText());
        if (role != UserRole.GERANT) expertise = null;

        User u = new User();
        u.setId(id);
        u.setNom(safe(nomField.getText()));
        u.setPrenom(safe(prenomField.getText()));
        u.setEmail(safe(emailField.getText()));
        u.setCin(safe(cinField.getText()));
        u.setNumTel(safe(telField.getText()));
        u.setDateN(safe(dateNField.getText()));
        u.setPassword(passwordField.getText());
        u.setRole(role);
        u.setExpertiseArea(expertise);
        return u;
    }

    private void fillForm(User u) {
        nomField.setText(u.getNom());
        prenomField.setText(u.getPrenom());
        emailField.setText(u.getEmail());
        cinField.setText(String.valueOf(u.getCIN()));
        telField.setText(u.getNumTel());
        dateNField.setText(u.getDateN());
        passwordField.setText(u.getPassword());
        roleCombo.setValue(u.getRole());

        boolean isGerant = (u.getRole() == UserRole.GERANT);
        expertiseField.setDisable(!isGerant);
        expertiseField.setText(isGerant ? (u.getExpertiseArea() != null ? u.getExpertiseArea() : "") : "");
    }

    private void clearForm() {
        nomField.clear();
        prenomField.clear();
        emailField.clear();
        cinField.clear();
        telField.clear();
        dateNField.clear();
        passwordField.clear();
        roleCombo.getSelectionModel().clearSelection();
        expertiseField.clear();
        expertiseField.setDisable(true);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
