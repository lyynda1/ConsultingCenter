package com.advisora.GUI.Auth;

import com.advisora.utils.SceneThemeApplier;

import com.advisora.Services.user.PasswordResetService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ForgotPasswordController {

    @FXML private TextField emailField;
    @FXML private Label emailError;
    @FXML private Label infoLabel;

    private final PasswordResetService resetService = new PasswordResetService();

    @FXML
    private void handleSendCode() {
        clear();

        String email = safe(emailField.getText());
        if (email.isEmpty()) {
            show(emailError, "Email is required");
            return;
        }

        boolean ok = resetService.requestCode(email); // should return false if user doesn't exist
        if (!ok) {
            show(emailError, "No account found for this email.");
            return;
        }

        show(infoLabel, "Code sent. Check your email.");
        openResetPage(email);
    }
    private void openResetPage(String email) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ResetPassword.fxml"));
            Parent root = loader.load();

            ResetPasswordController c = loader.getController();
            c.prefillEmail(email);

            Stage stage = (Stage) emailField.getScene().getWindow();
            SceneThemeApplier.setScene(stage, root);
            stage.setTitle("Advisora - Reset Password");
        } catch (Exception e) {
            e.printStackTrace();
            show(infoLabel, e.getMessage());
        }
    }

    @FXML
    private void handleBack() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/views/login.fxml"));
            Stage stage = (Stage) emailField.getScene().getWindow();
            SceneThemeApplier.setScene(stage, root);
            stage.setTitle("Advisora - Login");
        } catch (Exception e) {
            e.printStackTrace();
            show(infoLabel, e.getMessage());
        }
    }

    private void clear() {
        hide(emailError);
        hide(infoLabel);
    }

    private void show(Label l, String msg) {
        l.setText(msg);
        l.setVisible(true);
        l.setManaged(true);
    }

    private void hide(Label l) {
        l.setText("");
        l.setVisible(false);
        l.setManaged(false);
    }

    private String safe(String s){ return s == null ? "" : s.trim(); }
}


