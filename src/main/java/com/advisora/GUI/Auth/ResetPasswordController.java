package com.advisora.GUI.Auth;

import com.advisora.Services.user.PasswordResetService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

public class ResetPasswordController {

    @FXML private TextField emailField;
    @FXML private TextField codeField;
    @FXML private PasswordField newPassField;
    @FXML private PasswordField confirmPassField;
    @FXML private ImageView eyeIcon;
    @FXML private Label emailError;
    @FXML private Label codeError;
    @FXML private Label passError;
    @FXML private Label infoLabel;
    private final Image EYE_ON  = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeopen.png"));
    private final Image EYE_OFF = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeClosed.png"));
    private boolean passwordShown = false;
    @FXML private TextField newPassVisibleField;

    private final PasswordResetService resetService = new PasswordResetService();
    @FXML
    public void initialize() {
        if (newPassVisibleField != null && newPassField != null) {
            newPassVisibleField.textProperty().bindBidirectional(newPassField.textProperty());
            newPassVisibleField.setVisible(false);
            newPassVisibleField.setManaged(false);
        }
        if (eyeIcon != null) eyeIcon.setImage(EYE_ON);
    }
    public void prefillEmail(String email) {
        if (emailField != null) emailField.setText(email);
    }

    @FXML
    private void handleReset() {
        clear();

        String email = safe(emailField.getText());
        String code = safe(codeField.getText());
        String p1 = safe(newPassField.getText());
        String p2 = safe(confirmPassField.getText());

        boolean ok = true;
        if (email.isEmpty()) { show(emailError, "Email is required"); ok = false; }
        if (code.isEmpty()) { show(codeError, "Code is required"); ok = false; }
        if (p1.isEmpty()) { show(passError, "Password is required"); ok = false; }
        if (!p1.equals(p2)) { show(passError, "Passwords do not match"); ok = false; }
        if (p1.length() < 6) { show(passError, "Password must be at least 6 characters"); ok = false; }

        if (!ok) return;

        boolean done = resetService.verifyAndReset(email, code, p1);
        if (!done) {
            show(infoLabel, "Invalid/expired code. Please request a new one.");
            return;
        }

        show(infoLabel, "Password updated successfully. You can login now.");
    }

    @FXML
    private void handleBack() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/GUI/Auth/login.fxml"));
            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Advisora - Login");
        } catch (Exception e) {
            e.printStackTrace();
            show(infoLabel, e.getMessage());
        }
    }

    private void clear() {
        hide(emailError); hide(codeError); hide(passError); hide(infoLabel);
    }
    private void show(Label l, String msg) {
        if (l == null) return; // ✅ prevent crash
        l.setText(msg);
        l.setVisible(true);
        l.setManaged(true);
    }


    private void hide(Label l) {
        if (l == null) return; // ✅ prevent crash
        l.setText("");
        l.setVisible(false);
        l.setManaged(false);
    }
    @FXML
    private void handleTogglePassword() {
        passwordShown = !passwordShown;

        newPassVisibleField.setVisible(passwordShown);
        newPassVisibleField.setManaged(passwordShown);

        newPassField.setVisible(!passwordShown);
        newPassField.setManaged(!passwordShown);

        if (eyeIcon != null) eyeIcon.setImage(passwordShown ? EYE_OFF : EYE_ON);

        if (passwordShown) {
            newPassVisibleField.requestFocus();
            newPassVisibleField.positionCaret(newPassVisibleField.getText().length());
        } else {
            newPassField.requestFocus();
            newPassField.positionCaret(newPassField.getText().length());
        }
    }
    private String safe(String s){ return s == null ? "" : s.trim(); }
}