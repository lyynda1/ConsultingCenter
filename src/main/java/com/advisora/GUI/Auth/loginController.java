package com.advisora.GUI.Auth;

import com.advisora.Model.User;
import com.advisora.Services.SessionContext;
import com.advisora.Services.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class loginController {
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label emailError;
    @FXML
    private Label passwordError;
    @FXML
    private Label loginError;
    @FXML
    private Label statusLabel;

    private final UserService userService = new UserService();

    @FXML
    private void handleLogin() {
        clearErrors();
        statusLabel.setText("Checking credentials...");

        String email = safe(emailField.getText());
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        boolean valid = true;
        if (email.isBlank()) {
            showFieldError(emailField, emailError, "Email is required");
            valid = false;
        }
        if (password.isBlank()) {
            showFieldError(passwordField, passwordError, "Password is required");
            valid = false;
        }
        if (!valid) {
            statusLabel.setText("Validation failed.");
            return;
        }

        try {
            User user = userService.authenticate(email, password);
            if (user == null) {
                showLoginError("Invalid email or password");
                statusLabel.setText("Login failed.");
                return;
            }

            SessionContext.setCurrentUser(user.getId(), user.getRole());
            openGeneralInterface(user);
        } catch (Exception ex) {
            showLoginError(ex.getMessage());
            statusLabel.setText("Login error.");
        }
    }

    @FXML
    private void handleClear() {
        emailField.clear();
        passwordField.clear();
        clearErrors();
        statusLabel.setText("Ready.");
    }

    private void openGeneralInterface(User user) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/GUI/Admin/admin.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) emailField.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("Advisora - Interface Generale (" + user.getRole() + ")");
    }

    private void clearErrors() {
        hideFieldError(emailField, emailError);
        hideFieldError(passwordField, passwordError);
        loginError.setText("");
        loginError.setVisible(false);
        loginError.setManaged(false);
    }

    private void showLoginError(String message) {
        loginError.setText(message);
        loginError.setManaged(true);
        loginError.setVisible(true);
    }

    private void showFieldError(Control input, Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        if (!input.getStyleClass().contains("input-error")) {
            input.getStyleClass().add("input-error");
        }
    }

    private void hideFieldError(Control input, Label errorLabel) {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        input.getStyleClass().remove("input-error");
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
