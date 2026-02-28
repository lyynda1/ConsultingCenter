package com.advisora.GUI.Auth;

import com.advisora.Model.user.User;
import com.advisora.Services.user.UserService;
import com.advisora.enums.UserRole;
import com.advisora.utils.PythonRunner;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Control;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

public class SignupController {

    @FXML private TextField prenomField;
    @FXML private TextField nomField;
    @FXML private TextField cinField;
    @FXML private TextField telField;
    @FXML private TextField emailField;
    @FXML private TextField expertiseField;

    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private PasswordField confirmPasswordField;

    @FXML private ImageView eyeIcon;

    @FXML private Label prenomError;
    @FXML private Label nomError;
    @FXML private Label cinError;
    @FXML private Label telError;
    @FXML private Label emailError;
    @FXML private Label expertiseError;
    @FXML private Label passwordError;
    @FXML private Label confirmPasswordError;
    @FXML private Label formError;
    @FXML private Label statusLabel;

    private boolean passwordShown = false;

    private final Image EYE_ON  = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeopen.png"));
    private final Image EYE_OFF = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeClosed.png"));

    private final UserService userService = new UserService();

    private static final Pattern EMAIL_RX =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML
    public void initialize() {
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        passwordVisibleField.setVisible(false);
        passwordVisibleField.setManaged(false);

        passwordField.setVisible(true);
        passwordField.setManaged(true);

        if (eyeIcon != null) eyeIcon.setImage(EYE_ON);

        setupLiveValidation();
        if (statusLabel != null) statusLabel.setText("Ready.");
    }

    // =========================
    // LIVE VALIDATION
    // =========================
    private void setupLiveValidation() {
        prenomField.textProperty().addListener((o,a,b) -> validatePrenom());
        nomField.textProperty().addListener((o,a,b) -> validateNom());
        cinField.textProperty().addListener((o,a,b) -> validateCin());
        telField.textProperty().addListener((o,a,b) -> validateTel());
        emailField.textProperty().addListener((o,a,b) -> validateEmail());
        passwordField.textProperty().addListener((o,a,b) -> { validatePassword(); validateConfirmPassword(); });
        passwordVisibleField.textProperty().addListener((o,a,b) -> { validatePassword(); validateConfirmPassword(); });
        confirmPasswordField.textProperty().addListener((o,a,b) -> validateConfirmPassword());
    }

    private boolean validatePrenom() {
        String v = safe(prenomField.getText());
        if (v.isEmpty()) return err(prenomField, prenomError, "Prénom obligatoire");
        return ok(prenomField, prenomError);
    }

    private boolean validateNom() {
        String v = safe(nomField.getText());
        if (v.isEmpty()) return err(nomField, nomError, "Nom obligatoire");
        return ok(nomField, nomError);
    }

    private boolean validateCin() {
        String v = safe(cinField.getText());
        if (!v.matches("\\d{8}")) return err(cinField, cinError, "CIN = 8 chiffres");
        return ok(cinField, cinError);
    }

    private boolean validateTel() {
        String v = safe(telField.getText());
        if (!v.isEmpty() && !v.matches("\\d{8}")) return err(telField, telError, "Téléphone = 8 chiffres");
        return ok(telField, telError);
    }

    private boolean validateEmail() {
        String v = safe(emailField.getText());
        if (v.isEmpty() || !EMAIL_RX.matcher(v).matches())
            return err(emailField, emailError, "Email invalide");
        return ok(emailField, emailError);
    }

    private boolean validatePassword() {
        String v = passwordShown ? safe(passwordVisibleField.getText()) : safe(passwordField.getText());
        if (v.length() < 6) return err(passwordField, passwordError, "Mot de passe min 6 caractères");
        return ok(passwordField, passwordError);
    }

    private boolean validateConfirmPassword() {
        String p = passwordShown ? safe(passwordVisibleField.getText()) : safe(passwordField.getText());
        String c = safe(confirmPasswordField.getText());
        if (!c.equals(p)) return err(confirmPasswordField, confirmPasswordError, "Les mots de passe ne correspondent pas");
        return ok(confirmPasswordField, confirmPasswordError);
    }

    private boolean err(Control input, Label label, String msg) {
        if (label != null) {
            label.setText(msg);
            label.setVisible(true);
            label.setManaged(true);
        }
        if (input != null && !input.getStyleClass().contains("input-error")) {
            input.getStyleClass().add("input-error");
        }
        return false;
    }

    private boolean ok(Control input, Label label) {
        if (label != null) {
            label.setText("");
            label.setVisible(false);
            label.setManaged(false);
        }
        if (input != null) input.getStyleClass().remove("input-error");
        return true;
    }

    // =========================
    // REQUIRED BY FXML:
    // onAction="#handleTogglePassword"
    // =========================
    @FXML
    private void handleTogglePassword() {
        passwordShown = !passwordShown;

        passwordVisibleField.setVisible(passwordShown);
        passwordVisibleField.setManaged(passwordShown);

        passwordField.setVisible(!passwordShown);
        passwordField.setManaged(!passwordShown);

        if (eyeIcon != null) eyeIcon.setImage(passwordShown ? EYE_OFF : EYE_ON);

        if (passwordShown) {
            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
        } else {
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }

    // =========================
    // SIGNUP
    // =========================
    @FXML
    private void handleSignup() {
        clearErrors();
        if (statusLabel != null) statusLabel.setText("Creating account...");

        boolean valid =
                validatePrenom() &
                        validateNom() &
                        validateCin() &
                        validateTel() &
                        validateEmail() &
                        validatePassword() &
                        validateConfirmPassword();

        if (!valid) {
            if (statusLabel != null) statusLabel.setText("Validation failed.");
            return;
        }

        String prenom = safe(prenomField.getText());
        String nom = safe(nomField.getText());
        String cin = safe(cinField.getText());
        String tel = safe(telField.getText());
        String email = safe(emailField.getText());
        String password = passwordShown ? safe(passwordVisibleField.getText()) : safe(passwordField.getText());
        String expertise = safe(expertiseField.getText());

        try {
            // 1) Insert user
            User u = new User();
            u.setPrenom(prenom);
            u.setNom(nom);
            u.setCin(cin);
            u.setNumTel(tel.isEmpty() ? null : tel);
            u.setEmail(email);
            u.setPassword(password);
            u.setExpertiseArea(expertise.isEmpty() ? null : expertise);
            u.setRole(UserRole.CLIENT);

            userService.ajouter(u);

            System.out.println("[SIGNUP] NEW USER ID = " + u.getId());
            if (u.getId() <= 0) {
                showFormError("Signup created user but ID is missing (id=0). Check AUTO_INCREMENT / generated keys.");
                if (statusLabel != null) statusLabel.setText("Signup incomplete.");
                return;
            }

            if (statusLabel != null) statusLabel.setText("Account created (id=" + u.getId() + ").");

            // 2) Ask if scan now
            boolean scanNow = showFaceEnrollmentDialog();

            // 3) Run enrollment only if user said YES
            if (scanNow) {
                if (statusLabel != null) statusLabel.setText("Opening camera...");

                String pythonExe = resolvePythonExe();
                String enrollScript = resolvePythonScriptPath("enroll_face.py");

                PythonRunner.Result pr = PythonRunner.run(
                        pythonExe,
                        enrollScript,
                        String.valueOf(u.getId())
                );

                System.out.println("[ENROLL] exit=" + pr.exitCode);
                System.out.println("[ENROLL] cmd=" + String.join(" ", pr.cmd));
                System.out.println("[ENROLL] out=\n" + pr.out);

                // IMPORTANT: TensorFlow prints logs before OK, so parse ONLY the LAST OK line
                String okLine = findLastOkLine(pr.out);
                System.out.println("[ENROLL] okLine=" + okLine);

                if (pr.exitCode != 0 || okLine == null) {
                    showFormError("Face scan failed: " + (pr.out == null ? "" : pr.out));
                    if (statusLabel != null) statusLabel.setText("Face scan failed.");
                    return;
                } else {
                    String facePath = extractTokenValue(okLine, "face_path");
                    System.out.println("[ENROLL] PARSED facePath=" + facePath);

                    if (facePath == null || facePath.isBlank()) {
                        showFormError("Face scan OK but missing face_path.");
                        if (statusLabel != null) statusLabel.setText("Face scan incomplete.");
                        return;
                    } else {
                        // safety: reject truncated directory paths
                        String lower = facePath.toLowerCase();
                        boolean looksLikeImage = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
                        if (!looksLikeImage) {
                            showFormError("Face scan returned invalid face_path: " + facePath);
                            if (statusLabel != null) statusLabel.setText("Face not saved.");
                            return;
                        } else {
                            int rows = userService.updateFacePath(u.getId(), facePath);
                            System.out.println("[ENROLL] updateFacePath affected rows=" + rows);

                            if (rows <= 0) {
                                showFormError("Face scan OK but DB was not updated (rows=0). Check column name and idUser.");
                                if (statusLabel != null) statusLabel.setText("Face not saved.");
                                return;
                            } else {
                                if (statusLabel != null) statusLabel.setText("Face saved ✅");
                            }
                        }
                    }
                }
            }

            // 4) Back to login
            handleBackToLogin();

        } catch (Exception ex) {
            ex.printStackTrace();
            showFormError("Signup failed: " + ex.getMessage());
            if (statusLabel != null) statusLabel.setText("Signup error.");
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Auth/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) prenomField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Advisora - Login");
        } catch (Exception e) {
            e.printStackTrace();
            showFormError("Cannot open login: " + e.getMessage());
        }
    }

    // =========================
    // Fancy face enrollment dialog
    // =========================
    private boolean showFaceEnrollmentDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Face Enrollment");
        dialog.initModality(Modality.WINDOW_MODAL);

        DialogPane pane = dialog.getDialogPane();
        pane.setMinWidth(480);

        ButtonType scanBtn = new ButtonType("Scan now", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterBtn = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(scanBtn, laterBtn);

        Button scanButton = (Button) pane.lookupButton(scanBtn);
        scanButton.setStyle("-fx-background-color:#2563eb; -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:10;");

        Button laterButton = (Button) pane.lookupButton(laterBtn);
        laterButton.setStyle("-fx-background-color:transparent; -fx-border-color:#d1d5db; -fx-border-radius:10;");

        Label title = new Label("Enable Face Login");
        title.setStyle("-fx-font-size:16px; -fx-font-weight:700;");

        Label desc = new Label("Scan your face now to sign in faster next time.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill:#6b7280;");

        Label p1 = new Label("• Takes about 10 seconds");
        Label p2 = new Label("• Use good lighting");
        Label p3 = new Label("• You can redo this later");

        VBox textBox = new VBox(6, title, desc, new Separator(), p1, p2, p3);
        textBox.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("👤");
        icon.setStyle("-fx-font-size:22px;");
        VBox iconBox = new VBox(icon);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setMinSize(44, 44);
        iconBox.setStyle("-fx-background-color:#e0e7ff; -fx-background-radius:22;");

        HBox top = new HBox(12, iconBox, textBox);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(14));

        pane.setContent(top);

        return dialog.showAndWait().orElse(laterBtn) == scanBtn;
    }

    // =========================
    // ROBUST OUTPUT PARSING HELPERS
    // =========================
    private String findLastOkLine(String out) {
        if (out == null) return null;
        String[] lines = out.replace("\r", "").split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (t.startsWith("OK")) return t;
        }
        return null;
    }

    /**
     * Extracts value for key=... from a single OK line.
     * Works even when value contains spaces.
     *
     * Example:
     * OK face_path=C:\...\(3)\faces\enroll_54.jpg embedding_path=... samples=8
     */
    private String extractTokenValue(String line, String key) {
        if (line == null) return null;

        String needle = key + "=";
        int start = line.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();

        int end = line.length();

        int i1 = line.indexOf(" embedding_path=", start);
        if (i1 >= 0) end = Math.min(end, i1);

        int i2 = line.indexOf(" samples=", start);
        if (i2 >= 0) end = Math.min(end, i2);

        return line.substring(start, end).trim();
    }

    // =========================
    // UTILS
    // =========================
    private String resolvePythonExe() {
        String userDir = System.getProperty("user.dir");
        String userHome = System.getProperty("user.home");
        String condaPrefix = System.getenv("CONDA_PREFIX");
        String forcedPython = System.getenv("ADVISORA_PYTHON");

        String[] candidates = new String[] {
                forcedPython,
                new File(userDir, ".venv\\Scripts\\python.exe").getAbsolutePath(),
                condaPrefix == null ? null : new File(condaPrefix, "python.exe").getAbsolutePath(),
                new File(userHome, "anaconda3\\python.exe").getAbsolutePath(),
                new File(userHome, "miniconda3\\python.exe").getAbsolutePath()
        };

        for (String c : candidates) {
            if (c == null || c.isBlank()) continue;
            File f = new File(c);
            if (f.exists() && f.isFile()) return f.getAbsolutePath();
        }

        return "python";
    }

    private String resolvePythonScriptPath(String scriptName) {
        String userDir = System.getProperty("user.dir");
        String defaultRel = "python\\" + scriptName;

        List<File> candidates = List.of(
                new File(userDir, "Python\\" + scriptName),
                new File(userDir, "python\\" + scriptName),
                new File(userDir, "ConsultingCenter-gestionInvestissements2\\ConsultingCenter-gestionProjets (3)\\ConsultingCenter-gestionProjets\\Python\\" + scriptName),
                new File(userDir, "ConsultingCenter-gestionInvestissements2\\ConsultingCenter-gestionProjets (3)\\ConsultingCenter-gestionProjets\\python\\" + scriptName)
        );

        for (File c : candidates) {
            if (c.exists() && c.isFile()) return c.getAbsolutePath();
        }

        try (var stream = Files.find(Paths.get(userDir), 10,
                (p, a) -> a.isRegularFile()
                        && p.getFileName().toString().equalsIgnoreCase(scriptName)
                        && p.getParent() != null
                        && p.getParent().getFileName() != null
                        && p.getParent().getFileName().toString().equalsIgnoreCase("python"))) {
            Path found = stream.findFirst().orElse(null);
            if (found != null) return found.toFile().getAbsolutePath();
        } catch (Exception ignored) {
        }

        return defaultRel;
    }

    private void clearErrors() {
        ok(prenomField, prenomError);
        ok(nomField, nomError);
        ok(cinField, cinError);
        ok(telField, telError);
        ok(emailField, emailError);
        ok(expertiseField, expertiseError);
        ok(passwordField, passwordError);
        ok(confirmPasswordField, confirmPasswordError);

        if (formError != null) {
            formError.setText("");
            formError.setVisible(false);
            formError.setManaged(false);
        }
    }

    private void showFormError(String msg) {
        if (formError != null) {
            formError.setText(msg);
            formError.setVisible(true);
            formError.setManaged(true);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
