package com.advisora.GUI.Auth;

import com.advisora.Model.user.User;
import com.advisora.Services.user.AuthSessionService;
import com.advisora.Services.user.Login2FAService;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.user.UserService;
import com.advisora.utils.LocalSessionStore;
import com.advisora.utils.TokenUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.scene.control.Button;

public class VerifyLoginCodeController {

    @FXML private TextField emailField;
    @FXML private TextField codeField;

    // These labels exist inside boxes in FXML:
    @FXML private Label infoLabel;
    @FXML private Label codeError;
    @FXML private Button resendBtn;
    // IMPORTANT: these must be injected too (your FXML has fx:id="errorBox"/"infoBox")
    @FXML private VBox errorBox;
    @FXML private VBox infoBox;

    private final Login2FAService twoFA = new Login2FAService();
    private final UserService userService = new UserService();
    private final AuthSessionService authSessionService = new AuthSessionService();


    private static final int RESEND_COOLDOWN_SECONDS = 30;
    private Timeline resendTimeline;
    private int resendRemaining = 0;
    private boolean rememberMe = false;

    public void init(String email, boolean remember) {
        this.rememberMe = remember;

        if (emailField != null) {
            emailField.setText(email);
            emailField.setDisable(true);
        }

        // Démarre automatiquement le compte à rebours dès l'ouverture (30s)
        startResendCooldown(RESEND_COOLDOWN_SECONDS);
    }

    @FXML
    private void handleVerify() {
        System.out.println("[2FA] handleVerify clicked"); // <-- PROOF it runs

        try {
            hideError();
            hideInfo();

            String email = safe(emailField.getText());
            String code = safe(codeField.getText());

            if (code.isEmpty()) {
                showError("Code is required");
                return;
            }

            boolean ok = twoFA.verifyLoginCode(email, code);
            if (!ok) {
                showInfo("Invalid or expired code.");
                return;
            }

            User u = userService.getByEmail(email);
            if (u == null) {
                showInfo("Account not found.");
                return;
            }

            // create session
            String rawToken = TokenUtil.randomToken();
            authSessionService.createSession(u.getId(), rawToken, "Desktop", "127.0.0.1");

            if (rememberMe) LocalSessionStore.save(rawToken);
            else LocalSessionStore.clear();

            SessionContext.setCurrentUser(u);
            openGeneralInterface(u);

        } catch (Exception e) {
            e.printStackTrace();
            showInfo("Verify failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleResend() {
        System.out.println("[2FA] handleResend clicked");

        // Anti spam (si déjà en cooldown)
        if (resendRemaining > 0) return;

        hideError();
        hideInfo();

        String email = safe(emailField.getText());

        // Désactiver immédiatement + démarrer cooldown
        startResendCooldown(RESEND_COOLDOWN_SECONDS);

        try {
            twoFA.requestLoginCode(email);
            // message initial (le timer va ensuite afficher le compte à rebours)
            showInfo("Un nouveau code a été envoyé.");
        } catch (Exception e) {
            e.printStackTrace();
            showInfo("Échec d'envoi: " + e.getMessage());

            // Si tu veux réactiver direct en cas d’échec, dé-commente :
            // stopResendCooldown(true);
        }
    }
    private void startResendCooldown(int seconds) {
        resendRemaining = seconds;

        if (resendBtn != null) resendBtn.setDisable(true);

        // Afficher le message de cooldown dans infoLabel
        showInfo("Vous pourrez renvoyer un code dans " + resendRemaining + "s.");

        if (resendTimeline != null) resendTimeline.stop();

        resendTimeline = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            resendRemaining--;

            if (resendRemaining > 0) {
                showInfo("Vous pourrez renvoyer un code dans " + resendRemaining + "s.");
            } else {
                if (resendBtn != null) resendBtn.setDisable(false);
                hideInfo(); // ou showInfo("Vous pouvez renvoyer un code."); si tu préfères
                resendTimeline.stop();
            }
        }));

        resendTimeline.setCycleCount(Timeline.INDEFINITE);
        resendTimeline.playFromStart();
    }

    private void stopResendCooldown(boolean enableButton) {
        resendRemaining = 0;
        if (resendTimeline != null) resendTimeline.stop();
        if (resendBtn != null) resendBtn.setDisable(!enableButton);
    }
    @FXML
    private void handleBackToLogin() {
        try {
            stopResendCooldown(true);

            Parent root = FXMLLoader.load(getClass().getResource("/GUI/Auth/login.fxml"));
            Stage stage = (Stage) codeField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Advisora - Login");
        } catch (Exception e) {
            e.printStackTrace();
            showInfo("Back failed: " + e.getMessage());
        }
    }

    private void openGeneralInterface(User user) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/InterfaceGeneral.fxml"));
        Parent root = loader.load();

        Stage stage = (Stage) codeField.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("Advisora - Interface Generale (" + user.getRole() + ")");
    }

    // ===== UI helpers (BOXES, not only labels) =====
    private void showError(String msg) {
        if (codeError != null) codeError.setText(msg);

        if (errorBox != null) {
            errorBox.setVisible(true);
            errorBox.setManaged(true);
        } else if (codeError != null) {
            // fallback
            codeError.setVisible(true);
            codeError.setManaged(true);
        }
    }

    private void hideError() {
        if (codeError != null) codeError.setText("");

        if (errorBox != null) {
            errorBox.setVisible(false);
            errorBox.setManaged(false);
        } else if (codeError != null) {
            codeError.setVisible(false);
            codeError.setManaged(false);
        }
    }

    private void showInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg);

        if (infoBox != null) {
            infoBox.setVisible(true);
            infoBox.setManaged(true);
        } else if (infoLabel != null) {
            infoLabel.setVisible(true);
            infoLabel.setManaged(true);
        }
    }

    private void hideInfo() {
        if (infoLabel != null) infoLabel.setText("");

        if (infoBox != null) {
            infoBox.setVisible(false);
            infoBox.setManaged(false);
        } else if (infoLabel != null) {
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}