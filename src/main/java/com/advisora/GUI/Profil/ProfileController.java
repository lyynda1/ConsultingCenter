package com.advisora.GUI.Profil;

import com.advisora.Model.User;
import com.advisora.Services.SessionContext;
import com.advisora.Services.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.*;
import java.util.function.Consumer;

public class ProfileController {

    @FXML private ImageView profileImage;
    @FXML private TextField nomField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private Label statusLabel;

    private final UserService userService = new UserService();
    private User currentUser;
    private String selectedImagePath;

    // ✁Ecallback to close overlay
    private Runnable onClose = () -> {};

    public void setOnClose(Runnable onClose) {
        this.onClose = (onClose == null) ? () -> {} : onClose;
    }

    private final Image defaultAvatar =
            new Image(getClass().getResourceAsStream("/GUI/Admin/icons/profile.png"));

    @FXML
    public void initialize() {
        currentUser = SessionContext.getCurrentUser();

        nomField.setText(currentUser.getNom());
        prenomField.setText(currentUser.getPrenom());
        emailField.setText(currentUser.getEmail());

        if (currentUser.getImagePath() == null || currentUser.getImagePath().trim().isEmpty())
            profileImage.setImage(defaultAvatar);
        else
            profileImage.setImage(new Image(new File(currentUser.getImagePath()).toURI().toString()));

        selectedImagePath = currentUser.getImagePath();
    }

    @FXML
    private void handleChangePhoto() {
        try {
            FileChooser fc = new FileChooser();
            File chosen = fc.showOpenDialog(profileImage.getScene().getWindow());
            if (chosen == null) return;

            Path uploads = Paths.get("uploads");
            Files.createDirectories(uploads);

            Path dest = uploads.resolve("user_" + currentUser.getId() + ".jpg");
            Files.copy(chosen.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            selectedImagePath = dest.toString();
            profileImage.setImage(new Image(dest.toUri().toString()));
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error photo ❁E");
        }
    }

    @FXML
    private void handleSave() {
        try {
            currentUser.setNom(nomField.getText());
            currentUser.setPrenom(prenomField.getText());
            currentUser.setEmail(emailField.getText());
            currentUser.setImagePath(selectedImagePath);

            userService.modifier(currentUser);
            SessionContext.setCurrentUser(currentUser);

            statusLabel.setText("Saved ✁E");

            // ✁Eclose overlay and go back to previous page
            onClose.run();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error ❁E" + e.getMessage());
        }
    }

    // optional: if you add a Cancel button in FXML
    @FXML
    private void handleCancel() {
        onClose.run();
    }
}
