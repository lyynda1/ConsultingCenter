package com.advisora.GUI;

import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import com.advisora.utils.i18n.I18n;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HomeController {

    @FXML private Label lblRoleWelcome;
    private Runnable onOpenGames;

    @FXML
    public void initialize() {
        UserRole role = SessionContext.getCurrentRole();

        String key = switch (role) {
            case ADMIN -> "home.role.admin";
            case GERANT -> "home.role.gerant";
            case CLIENT -> "home.role.client";
        };

        lblRoleWelcome.setText(I18n.tr(key));
    }

    public void setOnOpenGames(Runnable onOpenGames) {
        this.onOpenGames = onOpenGames;
    }

    @FXML
    private void onOpenGames() {
        if (onOpenGames != null) onOpenGames.run();
    }
}