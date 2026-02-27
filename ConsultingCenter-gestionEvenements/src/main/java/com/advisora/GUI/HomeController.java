package com.advisora.GUI;

import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HomeController {
    @FXML private Label lblRoleWelcome;
    private Runnable onOpenGames;

    @FXML
    public void initialize() {
        UserRole role = SessionContext.getCurrentRole();
        String roleText = switch (role) {
            case ADMIN -> "Vous etes connecte en tant qu'ADMIN: vous avez acces global a la plateforme.";
            case GERANT -> "Vous etes connecte en tant que GERANT: vous pouvez piloter les decisions et les modules de gestion.";
            case CLIENT -> "Vous etes connecte en tant que CLIENT: vous pouvez suivre vos projets et vos reservations.";
        };
        lblRoleWelcome.setText(roleText);
    }

    public void setOnOpenGames(Runnable onOpenGames) {
        this.onOpenGames = onOpenGames;
    }

    @FXML
    private void onOpenGames() {
        if (onOpenGames != null) {
            onOpenGames.run();
        }
    }
}
