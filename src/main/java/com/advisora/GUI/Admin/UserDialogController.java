package com.advisora.GUI.Admin;

import com.advisora.Model.User;
import com.advisora.Services.UserService;
import com.advisora.enums.UserRole;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.*;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class UserDialogController {

    @FXML private Label titleLabel;

    @FXML private TextField nomField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private TextField cinField;
    @FXML private TextField telField;
    @FXML private TextField dateNField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<UserRole> roleCombo;
    @FXML private TextField expertiseField;
    @FXML private ImageView userImageView;

    private String selectedImagePath;
    private final Image defaultAvatar =
            new Image(getClass().getResourceAsStream("/GUI/Admin/icons/profile.png"));

    @FXML private Label nomError;
    @FXML private Label prenomError;
    @FXML private Label emailError;
    @FXML private Label cinError;
    @FXML private Label telError;
    @FXML private Label dateNError;
    @FXML private Label passwordError;
    @FXML private Label roleError;
    @FXML private Label expertiseError;


    private final UserService userService = new UserService();

    private boolean editMode = false;
    private User editingUser;

    private Runnable onClose = () -> {};
    private Consumer<String> onSuccess = msg -> {};

    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L} \\-']{1,49}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern CIN_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{8,15}$");
    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void initAdd(Consumer<String> onSuccess, Runnable onClose) {
        this.onSuccess = onSuccess;
        this.onClose = onClose;
        this.editMode = false;
        this.editingUser = null;
        userImageView.setImage(defaultAvatar);
        selectedImagePath = null;
        titleLabel.setText("Nouvel Utilisateur");
        roleCombo.getItems().setAll(UserRole.values());

        passwordField.setDisable(false);
        passwordField.setPromptText("********");

        expertiseField.setDisable(true);

        roleCombo.valueProperty().addListener((o,a,b) -> toggleExpertise(b));
        addLiveValidation();
    }

    public void initEdit(User u, Consumer<String> onSuccess, Runnable onClose) {
        this.onSuccess = onSuccess;
        this.onClose = onClose;
        this.editMode = true;
        this.editingUser = u;

        titleLabel.setText("Modifier Utilisateur #" + u.getId());
        roleCombo.getItems().setAll(UserRole.values());

        fill(u);
        if (u.getImagePath() == null || u.getImagePath().trim().isEmpty()) {
            userImageView.setImage(defaultAvatar);
        } else {
            userImageView.setImage(new Image(new File(u.getImagePath()).toURI().toString()));
        }
        selectedImagePath = u.getImagePath();

        // ✁Eadmin can't modify password
        passwordField.clear();
        passwordField.setDisable(true);
        passwordField.setPromptText("Non modifiable");

        roleCombo.valueProperty().addListener((o,a,b) -> toggleExpertise(b));
        addLiveValidation();
    }

    private void fill(User u) {
        nomField.setText(u.getNom());
        prenomField.setText(u.getPrenom());
        emailField.setText(u.getEmail());
        cinField.setText(u.getCin());
        telField.setText(u.getNumTel());
        dateNField.setText(u.getDateN());
        roleCombo.setValue(u.getRole());
        expertiseField.setText(u.getExpertiseArea() == null ? "" : u.getExpertiseArea());
        toggleExpertise(u.getRole());
    }

    private void toggleExpertise(UserRole r) {
        boolean isGerant = r == UserRole.GERANT;
        expertiseField.setDisable(!isGerant);
        if (!isGerant) expertiseField.clear();
        hideError(expertiseField, expertiseError);
        hideError(roleCombo, roleError);
    }

    @FXML
    private void close() { onClose.run(); }
    @FXML
    private void handleChangePhoto() {
        try {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
            );

            Stage stage = (Stage) userImageView.getScene().getWindow();
            File chosen = fc.showOpenDialog(stage);
            if (chosen == null) return;

            Path uploads = Paths.get("uploads");
            Files.createDirectories(uploads);

            String ext = chosen.getName().toLowerCase().endsWith(".png") ? ".png" : ".jpg";
            Path dest = uploads.resolve("user_" + System.currentTimeMillis() + ext);
            Files.copy(chosen.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            selectedImagePath = dest.toString();
            userImageView.setImage(new Image(dest.toUri().toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void save() {
        clearErrors();
        if (!validateForm()) return;

        User u = new User();
        if (editMode) u.setId(editingUser.getId());

        u.setNom(safe(nomField.getText()));
        u.setPrenom(safe(prenomField.getText()));
        u.setEmail(safe(emailField.getText()));
        u.setCin(safe(cinField.getText()));
        u.setNumTel(safe(telField.getText()));
        u.setDateN(safe(dateNField.getText()));
        u.setRole(roleCombo.getValue());

        if (u.getRole() == UserRole.GERANT) u.setExpertiseArea(safe(expertiseField.getText()));
        else u.setExpertiseArea(null);

        // ✁Epassword:
        // ADD => required
        // EDIT => null (service must keep old password)
        if (!editMode) u.setPassword(passwordField.getText());
        else u.setPassword(null);

        try {
            if (!editMode) {
                u.setImagePath(selectedImagePath);
                userService.ajouter(u);
                onSuccess.accept("Utilisateur ajouté ✁E");
            } else {
                u.setImagePath(selectedImagePath);
                userService.modifier(u);
                onSuccess.accept("Utilisateur modifié ✁E");
            }
            onClose.run();
        } catch (Exception e) {
            // simple feedback in error labels
            showError(emailField, emailError, "Erreur: " + e.getMessage());
        }

    }

    // ---------- Validation ----------
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

        if (nom.trim().isEmpty()) ok &= showError(nomField, nomError, "Nom obligatoire");
        else if (!NAME_PATTERN.matcher(nom).matches()) ok &= showError(nomField, nomError, "Nom invalide");
        else hideError(nomField, nomError);

        if (prenom.trim().isEmpty()) ok &= showError(prenomField, prenomError, "Prénom obligatoire");
        else if (!NAME_PATTERN.matcher(prenom).matches()) ok &= showError(prenomField, prenomError, "Prénom invalide");
        else hideError(prenomField, prenomError);

        if (email.trim().isEmpty()) ok &= showError(emailField, emailError, "Email obligatoire");
        else if (!EMAIL_PATTERN.matcher(email).matches()) ok &= showError(emailField, emailError, "Email invalide");
        else hideError(emailField, emailError);

        if (cin.trim().isEmpty()) ok &= showError(cinField, cinError, "CIN obligatoire");
        else if (!CIN_PATTERN.matcher(cin).matches()) ok &= showError(cinField, cinError, "CIN invalide (8 chiffres)");
        else hideError(cinField, cinError);

        if (tel.trim().isEmpty()) ok &= showError(telField, telError, "Téléphone obligatoire");
        else if (!PHONE_PATTERN.matcher(tel).matches()) ok &= showError(telField, telError, "Téléphone invalide");
        else hideError(telField, telError);

        if (dobTxt.trim().isEmpty()) {
            ok &= showError(dateNField, dateNError, "Date obligatoire (YYYY-MM-DD)");
        } else {
            try {
                LocalDate dob = LocalDate.parse(dobTxt, DOB_FMT);
                if (dob.isAfter(LocalDate.now())) ok &= showError(dateNField, dateNError, "Date invalide (futur)");
                else {
                    int age = Period.between(dob, LocalDate.now()).getYears();
                    if (age < 10) ok &= showError(dateNField, dateNError, "Âge invalide (<10)");
                    else hideError(dateNField, dateNError);
                }
            } catch (DateTimeParseException ex) {
                ok &= showError(dateNField, dateNError, "Format: YYYY-MM-DD");
            }
        }

        if (!editMode) {
            if (pass.trim().isEmpty()) ok &= showError(passwordField, passwordError, "Mot de passe obligatoire");
            else hideError(passwordField, passwordError);
        } else {
            hideError(passwordField, passwordError);
        }

        if (role == null) ok &= showError(roleCombo, roleError, "Choisir un rôle");
        else hideError(roleCombo, roleError);

        if (role == UserRole.GERANT) {
            if (expertise.trim().isEmpty()) ok &= showError(expertiseField, expertiseError, "Expertise obligatoire");
            else hideError(expertiseField, expertiseError);
        } else {
            hideError(expertiseField, expertiseError);
        }

        return ok;
    }

    // ✁ERouge dynamique pendant qu'il tape
    private void addLiveValidation() {
        liveValidate(nomField, nomError, NAME_PATTERN, "Nom obligatoire", "Nom invalide");
        liveValidate(prenomField, prenomError, NAME_PATTERN, "Prénom obligatoire", "Prénom invalide");
        liveValidate(emailField, emailError, EMAIL_PATTERN, "Email obligatoire", "Email invalide");
        liveValidate(cinField, cinError, CIN_PATTERN, "CIN obligatoire", "CIN invalide (8 chiffres)");
        liveValidate(telField, telError, PHONE_PATTERN, "Téléphone obligatoire", "Téléphone invalide");

        dateNField.textProperty().addListener((obs, a, v) -> {
            String s = safe(v);
            if (s.trim().isEmpty()) { showError(dateNField, dateNError, "Date obligatoire"); return; }
            try {
                LocalDate.parse(s, DOB_FMT);
                hideError(dateNField, dateNError);
            } catch (Exception ex) {
                showError(dateNField, dateNError, "Format: YYYY-MM-DD");
            }
        });

        roleCombo.valueProperty().addListener((o,a,b) -> hideError(roleCombo, roleError));
        expertiseField.textProperty().addListener((o,a,b) -> hideError(expertiseField, expertiseError));

        if (!editMode) {
            passwordField.textProperty().addListener((o,a,b) -> {
                if (safe(b).trim().isEmpty()) showError(passwordField, passwordError, "Mot de passe obligatoire");
                else hideError(passwordField, passwordError);
            });
        }
    }

    private void liveValidate(TextField field, Label err, Pattern pattern,
                              String emptyMsg, String invalidMsg) {
        field.textProperty().addListener((obs, oldV, v) -> {
            String s = safe(v);
            if (s.trim().isEmpty()) { showError(field, err, emptyMsg); return; }
            if (pattern != null && !pattern.matcher(s).matches()) { showError(field, err, invalidMsg); return; }
            hideError(field, err);
        });
    }

    private boolean showError(Control field, Label err, String msg) {
        err.setText(msg);
        err.setManaged(true);
        err.setVisible(true);
        if (!field.getStyleClass().contains("input-error")) field.getStyleClass().add("input-error");
        return false;
    }

    private void hideError(Control field, Label err) {
        err.setText("");
        err.setVisible(false);
        err.setManaged(false);
        field.getStyleClass().remove("input-error");
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

    private String safe(String s) { return s == null ? "" : s.trim(); }
}
