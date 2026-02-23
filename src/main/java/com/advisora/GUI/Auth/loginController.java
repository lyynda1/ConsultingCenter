package com.advisora.GUI.Auth;

import com.advisora.Model.user.User;
import com.advisora.Services.user.SessionContext;
import com.advisora.Services.user.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class loginController {


    @FXML private TextField emailField;
    @FXML private ImageView eyeIcon;

    private final Image EYE_ON  = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeopen.png"));
    private final Image EYE_OFF = new Image(getClass().getResourceAsStream("/GUI/Admin/icons/eyeClosed.png"));
    private boolean passwordShown = false;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;

    @FXML private Label emailError;
    @FXML private Label passwordError;
    @FXML private Label loginError;
    @FXML private Label statusLabel;



    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        // icon initial (password hidden)
        eyeIcon.setImage(EYE_ON);

        // Enter => login
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

    @FXML
    private void handleTogglePassword() {
        passwordShown = !passwordShown;

        passwordVisibleField.setVisible(passwordShown);
        passwordVisibleField.setManaged(passwordShown);

        passwordField.setVisible(!passwordShown);
        passwordField.setManaged(!passwordShown);

        // toggle icon
        eyeIcon.setImage(passwordShown ? EYE_OFF : EYE_ON);

        // keep caret
        if (passwordShown) {
            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
        } else {
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }


    @FXML
    private void handleLogin() {
        clearErrors();
        statusLabel.setText("Checking credentials...");

        String email = safe(emailField.getText());

        // take password from whichever field is currently shown (they are bound anyway)
        String password = safe(passwordField.getText());

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

            SessionContext.setCurrentUser(user);

            openGeneralInterface(user);
        } catch (Exception ex) {
            ex.printStackTrace();
            showLoginError(ex.getMessage());
            statusLabel.setText("Login error.");
        }
    }

    @FXML
    private void handleClear() {
        emailField.clear();
        passwordField.clear(); // clears both because of binding
        clearErrors();
        statusLabel.setText("Ready.");
    }

    private void openGeneralInterface(User user) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/InterfaceGeneral.fxml"));
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
