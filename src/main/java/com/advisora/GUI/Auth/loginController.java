package com.advisora.GUI.Auth;

import com.advisora.Model.user.User;
import com.advisora.Services.user.AuthSessionService;
import com.advisora.Services.user.Login2FAService;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.user.UserService;
import com.advisora.utils.LocalSessionStore;
import com.advisora.utils.PythonRunner;
import com.advisora.utils.SceneThemeApplier;
import com.advisora.utils.TokenUtil;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import com.advisora.utils.i18n.FxLoader;
import com.advisora.utils.i18n.FxLoader.Loaded;
import com.advisora.utils.i18n.I18n;

public class loginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private ImageView eyeIcon;
    @FXML private CheckBox rememberMe;

    @FXML private Label emailError;
    @FXML private Label passwordError;
    @FXML private Label loginError;
    @FXML private Label statusLabel;

    private final Image EYE_ON  = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeopen.png"));
    private final Image EYE_OFF = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeClosed.png"));
    private boolean passwordShown = false;

    private final Login2FAService twoFA = new Login2FAService();
    private final AuthSessionService authSessionService = new AuthSessionService();
    private final UserService userService = new UserService();

    // =========================
    // INIT
    // =========================
    @FXML
    public void initialize() {

        // bind show/hide password fields
        if (passwordVisibleField != null && passwordField != null) {
            passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        }
        if (eyeIcon != null) eyeIcon.setImage(EYE_ON);

        // ENTER = login
        if (emailField != null) {
            emailField.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setOnKeyPressed(e -> {
                        if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                            handleLogin();
                            e.consume();
                        }
                    });
                }
            });
        }

        if (statusLabel != null) statusLabel.setText("Ready.");

        // âœ… auto-login after scene/window exists
        Platform.runLater(this::tryAutoLogin);
    }
    private Parent loadView(String fxmlPath) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath), com.advisora.utils.i18n.I18n.bundle());
        return loader.load();
    }

    private void tryAutoLogin() {
        try {
            String token = LocalSessionStore.load();
            if (token == null) return;

            // validate session in DB (not expired, not revoked)
            if (!authSessionService.isSessionValid(token)) {
                LocalSessionStore.clear();
                return;
            }

            Integer uid = authSessionService.getUserIdByToken(token);
            if (uid == null) {
                LocalSessionStore.clear();
                return;
            }

            User u = userService.getById(uid);
            if (u == null) {
                LocalSessionStore.clear();
                return;
            }

            SessionContext.setCurrentUser(u);
            openGeneralInterface(u);

        } catch (Exception e) {
            e.printStackTrace();
            LocalSessionStore.clear();
        }
    }

    // =========================
    // NAVIGATION
    // =========================
    @FXML

    private void handleOpenForgotPassword() {
        try {
            Parent root = FxLoader.load("/views/ForgotPassword.fxml");
            Stage stage = (Stage) emailField.getScene().getWindow();
            SceneThemeApplier.setScene(stage, root);
            stage.setTitle("Advisora - Forgot Password");
        } catch (Exception e) {
            e.printStackTrace();
            showLoginError(e.getMessage());
        }
    }

    @FXML
    private void handleOpenSignup() {
        try {
            Parent root = FxLoader.load("/views/Signup.fxml");
            Stage stage = (Stage) emailField.getScene().getWindow();
            SceneThemeApplier.setScene(stage, root);
            stage.setTitle("Advisora - Sign up");
        } catch (Exception e) {
            e.printStackTrace();
            showLoginError(e.getMessage());
        }
    }
    @FXML
    private void openGeneralInterface(User user) throws Exception {
        Parent root = FxLoader.load("/views/InterfaceGeneral.fxml");
        Stage stage = (Stage) emailField.getScene().getWindow();
        SceneThemeApplier.setScene(stage, root);
        stage.setTitle("Advisora - Interface Generale (" + user.getRole() + ")");
    }
    @FXML
    private void openVerifyLoginCodePage(String email, boolean remember) {
        try {
            Loaded<VerifyLoginCodeController> loaded =
                    FxLoader.loadWithController("/GUI/Auth/VerifyLoginCode.fxml");

            loaded.controller.init(email, remember);

            Stage stage = (Stage) emailField.getScene().getWindow();
            SceneThemeApplier.setScene(stage, loaded.root);
            stage.setTitle("Advisora - Verify Login Code");

        } catch (Exception e) {
            e.printStackTrace();
            showLoginError("Open verify page failed: " + e.getMessage());
        }
    }

    // =========================
    // NORMAL LOGIN (WITH 2FA)
    // =========================
    @FXML
    private void handleLogin() {
        clearErrors();
        if (statusLabel != null) statusLabel.setText("Checking credentials...");

        String email = safe(emailField.getText());
        String password = safe(passwordField.getText());
        // 0) check locked
        // 0) check locked
        if (userService.isLocked(email)) {
            long sec = userService.getLockRemainingSeconds(email);

            // âœ… if timer finished -> allow login (don't show 0 min)
            if (sec <= 0) {
                // optionnel: si ton userService garde encore "locked" alors qu'il est fini,
                // il faut le dÃ©verrouiller cÃ´tÃ© DB/service (voir plus bas).
            } else {
                if (sec < 60) {
                    showLoginError("Tentative de connexion 3/3. RÃ©essayez dans " + sec + " s.");
                } else {
                    long min = (sec + 59) / 60; // ceil
                    showLoginError("Tentative de connexion 3/3. RÃ©essayez dans " + min + " min.");
                }
                return;
            }
        }

        boolean valid = true;

        if (email.isEmpty()) {
            showFieldError(emailField, emailError, "Email is required");
            valid = false;
        }
        if (password.isEmpty()) {
            showFieldError(passwordField, passwordError, "Password is required");
            valid = false;
        }
        if (!valid) {
            if (statusLabel != null) statusLabel.setText("Validation failed.");
            return;
        }

        try {
            User user = userService.authenticate(email, password);
            if (user == null) {
                int count = userService.registerLoginFailure(email, 10); // âš ï¸ si authenticate le fait dÃ©jÃ  -> NE PAS doubler
                showLoginError("Invalid email or password");
                showLoginError("Email ou mot de passe incorrect (" + Math.min(count,3) + "/3)");


                if (statusLabel != null) statusLabel.setText("Login failed.");
                return;
            }

            // âœ… STEP 2FA: send code + open verify page then STOP
            boolean remember = (rememberMe != null && rememberMe.isSelected());

            // send OTP to email
            twoFA.requestLoginCode(user.getEmail());

            if (statusLabel != null) statusLabel.setText("Verification code sent.");
            openVerifyLoginCodePage(user.getEmail(), remember);

            // IMPORTANT: do NOT create session here.
            // Session is created only after code verification in VerifyLoginCodeController.

        } catch (Exception ex) {
            ex.printStackTrace();
            showLoginError(ex.getMessage());
            if (statusLabel != null) statusLabel.setText("Login error.");
        }
    }

    @FXML
    private void handleClear() {
        if (emailField != null) emailField.clear();
        if (passwordField != null) passwordField.clear();
        clearErrors();
        if (statusLabel != null) statusLabel.setText("Ready.");
    }

    // =========================
    // TOGGLE PASSWORD
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
    // FACE LOGIN (optional)
    // =========================
    @FXML
    private void handleFaceLogin() {
        clearErrors();
        if (statusLabel != null) statusLabel.setText("Scanning face...");

        try {
            File indexFile = buildUsersIndexFile();
            if (indexFile == null || !indexFile.exists() || indexFile.length() == 0) {
                showLoginError("No user enrolment found. Please sign up and scan first.");
                if (statusLabel != null) statusLabel.setText("Face login unavailable.");
                return;
            }

            int enrolledCount = countIndexLines(indexFile);
            if (enrolledCount < 2) {
                showLoginError("Face Login requires at least 2 enrolled users for reliability.");
                if (statusLabel != null) statusLabel.setText("Face login unavailable.");
                return;
            }

            String pythonExe = resolvePythonExe();
            String faceLoginScript = resolvePythonScriptPath("facelogin.py");

            PythonRunner.Result pr =
                    PythonRunner.run(pythonExe, faceLoginScript, indexFile.getAbsolutePath());

            String lastLine = findLastOkOrFailLine(pr.out);

            if (pr.exitCode != 0 || lastLine == null || !lastLine.startsWith("OK")) {
                showLoginError(prettyFaceError(lastLine != null ? lastLine : pr.out));
                if (statusLabel != null) statusLabel.setText("Face login failed.");
                return;
            }

            String email = parseToken(lastLine, "email");
            if (email == null || email.isBlank()) {
                showLoginError("Face login failed: missing email.");
                if (statusLabel != null) statusLabel.setText("Face login failed.");
                return;
            }

            double sim = parseDouble(parseToken(lastLine, "sim"), -1.0);
            double margin = parseDouble(parseToken(lastLine, "margin"), 0.0);

            final double SIM_MIN = 0.60;
            final double MARGIN_MIN = 0.05;

            if (sim < SIM_MIN || margin < MARGIN_MIN) {
                showLoginError("Face not recognized. Please try again or login with email/password.");
                if (statusLabel != null) statusLabel.setText("Face login failed.");
                return;
            }

            User user = userService.getByEmail(email);
            if (user == null) {
                showLoginError("Face matched, but no account exists for this email.");
                if (statusLabel != null) statusLabel.setText("Face login blocked.");
                return;
            }

            // âœ… FACE LOGIN: directly create session (no 2FA, unless you want it too)
            String rawToken = TokenUtil.randomToken();
            authSessionService.createSession(user.getId(), rawToken, "Desktop", "127.0.0.1");
            LocalSessionStore.save(rawToken);

            SessionContext.setCurrentUser(user);
            openGeneralInterface(user);

        } catch (Exception ex) {
            ex.printStackTrace();
            showLoginError("Face login error: " + ex.getMessage());
            if (statusLabel != null) statusLabel.setText("Face login error.");
        }
    }

    // =========================
    // OPTIONAL LOGOUT HELPER
    // =========================
    public void logout() {
        try {
            String token = LocalSessionStore.load();
            authSessionService.revokeSession(token);
        } catch (Exception ignored) {}

        LocalSessionStore.clear();
        SessionContext.clear();
    }

    // =========================
    // FACE INDEX HELPERS
    // =========================
    private int countIndexLines(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            int n = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) n++;
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    private String findLastOkOrFailLine(String out) {
        if (out == null) return null;
        String[] lines = out.replace("\r", "").split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String s = lines[i].trim();
            if (s.startsWith("OK")) return s;
            if (s.startsWith("FAIL")) return s;
        }
        String t = out.trim();
        return t.isEmpty() ? null : t;
    }

    private String parseToken(String out, String key) {
        if (out == null) return null;
        var m = Pattern.compile(key + "=([^\\s]+)").matcher(out);
        return m.find() ? m.group(1).trim() : null;
    }

    private double parseDouble(String s, double def) {
        try {
            if (s == null) return def;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private String prettyFaceError(String raw) {
        if (raw == null) return "Face login failed.";
        raw = raw.trim();
        if (raw.isEmpty()) return "Face login failed.";

        if (raw.contains("no_enrolled_users") || raw.contains("missing_index_file")) {
            return "No user enrolment found. Please sign up and scan first.";
        }
        if (raw.contains("no_valid_face_images")) {
            return "Face images missing/unreadable. Please re-enroll your face.";
        }
        if (raw.contains("camera_not_opened")) {
            return "Camera not available. Close other apps using the camera.";
        }
        if (raw.contains("model_weights_missing")) {
            return "Face model is missing. Connect internet once and retry to download model weights.";
        }
        if (raw.contains("no_face_detected")) {
            return "No face detected. Look at the camera with good lighting.";
        }
        if (raw.contains("not_recognized")) {
            return "Face not recognized. Please try again or login with email/password.";
        }
        if (raw.contains("timeout")) {
            return "Scan timed out. Try again.";
        }
        if (raw.contains("frame_error")) {
            return "Camera frame error. Try reconnecting the camera.";
        }
        if (raw.contains("cancelled")) {
            return "Face scan cancelled.";
        }
        if (raw.startsWith("FAIL")) return "Face login failed: " + raw;
        return "Face login failed.";
    }

    private File buildUsersIndexFile() throws Exception {
        List<User> users = userService.getUsersWithFacePath();

        File index = new File("faces", "users_index.txt");
        index.getParentFile().mkdirs();

        int written = 0;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(index), StandardCharsets.UTF_8))) {

            for (User u : users) {
                if (u == null) continue;

                String email = (u.getEmail() == null) ? "" : u.getEmail().trim();
                String facePath = (u.getFacePath() == null) ? "" : u.getFacePath().trim();
                if (email.isEmpty() || facePath.isEmpty()) continue;

                facePath = cutAt(facePath, " samples=");
                facePath = cutAt(facePath, " embedding_path=");
                facePath = facePath.trim();

                String resolvedFacePath = resolveFacePathForCurrentMachine(facePath);
                if (resolvedFacePath == null) {
                    System.out.println("[FACEINDEX] skip unreadable face_path for " + email + " => " + facePath);
                    continue;
                }

                String lower = resolvedFacePath.toLowerCase();
                boolean ok = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
                if (!ok) {
                    System.out.println("[FACEINDEX] skip bad face_path for " + email + " => " + resolvedFacePath);
                    continue;
                }

                pw.println(email + "|" + resolvedFacePath);
                if (u.getId() > 0 && !resolvedFacePath.equals(facePath)) {
                    try {
                        userService.updateFacePath(u.getId(), resolvedFacePath);
                    } catch (Exception ignored) {
                    }
                }
                written++;
            }
        }

        System.out.println("Face index generated: " + index.getAbsolutePath() + " users=" + written);
        return index;
    }

    private String cutAt(String s, String token) {
        int i = s.indexOf(token);
        return (i > 0) ? s.substring(0, i) : s;
    }

    private String resolveFacePathForCurrentMachine(String rawFacePath) {
        if (rawFacePath == null || rawFacePath.isBlank()) return null;

        File direct = new File(rawFacePath.trim());
        if (direct.exists() && direct.isFile()) return direct.getAbsolutePath();

        String fileName = direct.getName();
        if (fileName == null || fileName.isBlank()) return null;

        String baseName = fileName;
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) baseName = fileName.substring(0, dot);

        for (File dir : candidateFacesDirs()) {
            File c1 = new File(dir, fileName);
            if (c1.exists() && c1.isFile()) return c1.getAbsolutePath();

            File c2 = new File(dir, baseName + ".jpg");
            if (c2.exists() && c2.isFile()) return c2.getAbsolutePath();

            File c3 = new File(dir, baseName + ".jpeg");
            if (c3.exists() && c3.isFile()) return c3.getAbsolutePath();

            File c4 = new File(dir, baseName + ".png");
            if (c4.exists() && c4.isFile()) return c4.getAbsolutePath();
        }

        return null;
    }

    private List<File> candidateFacesDirs() {
        String userDir = System.getProperty("user.dir");
        List<File> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        List<File> bases = List.of(
                new File(userDir, "faces"),
                new File(userDir, "ConsultingCenter-gestionInvestissements2\\ConsultingCenter-gestionProjets (3)\\ConsultingCenter-gestionProjets\\faces")
        );

        for (File b : bases) {
            if (b.exists() && b.isDirectory() && seen.add(b.getAbsolutePath())) out.add(b);
        }

        try (var stream = Files.find(Paths.get(userDir), 10,
                (p, a) -> a.isDirectory()
                        && p.getFileName() != null
                        && p.getFileName().toString().equalsIgnoreCase("faces"))) {
            stream.forEach(p -> {
                File f = p.toFile();
                if (seen.add(f.getAbsolutePath())) out.add(f);
            });
        } catch (Exception ignored) {
        }

        return out;
    }

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

    // =========================
    // UI ERROR HELPERS
    // =========================
    private void clearErrors() {
        hideFieldError(emailField, emailError);
        hideFieldError(passwordField, passwordError);
        if (loginError != null) {
            loginError.setText("");
            loginError.setVisible(false);
            loginError.setManaged(false);
        }
    }

    private void showLoginError(String message) {
        if (loginError != null) {
            loginError.setText(message);
            loginError.setManaged(true);
            loginError.setVisible(true);
        }
    }

    private void showFieldError(Control input, Label errorLabel, String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setManaged(true);
            errorLabel.setVisible(true);
        }
        if (input != null && !input.getStyleClass().contains("input-error")) {
            input.getStyleClass().add("input-error");
        }
    }

    private void hideFieldError(Control input, Label errorLabel) {
        if (errorLabel != null) {
            errorLabel.setText("");
            errorLabel.setManaged(false);
            errorLabel.setVisible(false);
        }
        if (input != null) input.getStyleClass().remove("input-error");
    }
    @FXML
    private void handleGoogleLogin() {
        if (statusLabel != null) statusLabel.setText("Google login (coming soon).");
    }

    @FXML
    private void handleFacebookLogin() {
        if (statusLabel != null) statusLabel.setText("Facebook login (coming soon).");
    }
    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

