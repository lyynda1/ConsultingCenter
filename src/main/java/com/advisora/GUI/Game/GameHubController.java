package com.advisora.GUI.Game;

import com.advisora.GUI.Game.api.PlayableGame;
import com.advisora.Model.game.GameDescriptor;
import com.advisora.Services.game.GameRegistry;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class GameHubController {

    @FXML private ListView<GameDescriptor> gameList;
    @FXML private Button btnPlay;
    @FXML private Button btnReset;
    @FXML private Button btnBack;
    @FXML private StackPane gameHost;
    @FXML private Label lblStatus;

    private final GameRegistry gameRegistry = new GameRegistry();
    private PlayableGame currentGame;
    private Runnable onBack;
    private Timeline statusTicker;

    @FXML
    public void initialize() {
        gameList.setItems(FXCollections.observableArrayList(gameRegistry.availableGames()));
        gameList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(GameDescriptor item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.getDisplayName() + "\n" + item.getDescription());
            }
        });
        gameList.getSelectionModel().selectFirst();
        lblStatus.setText("Selectionnez un jeu puis cliquez Play.");

        statusTicker = new Timeline(new KeyFrame(Duration.millis(250), e -> {
            if (currentGame != null) {
                lblStatus.setText(currentGame.statusText());
            }
        }));
        statusTicker.setCycleCount(Timeline.INDEFINITE);
        statusTicker.play();
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @FXML
    private void onPlay() {
        GameDescriptor selected = gameList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Jeux", "Selectionnez un jeu.");
            return;
        }

        try {
            if (currentGame != null) {
                currentGame.stop();
            }
            currentGame = gameRegistry.build(selected.getId());
            Node gameView = currentGame.createView();
            gameView.setFocusTraversable(true);
            gameHost.getChildren().setAll(gameView);
            currentGame.start();
            lblStatus.setText(currentGame.statusText());
            Platform.runLater(gameView::requestFocus);
        } catch (Exception ex) {
            showError("Jeux", "Impossible de lancer le jeu: " + ex.getMessage());
            lblStatus.setText("Erreur de chargement du jeu.");
        }
    }

    @FXML
    private void onReset() {
        if (currentGame == null) {
            lblStatus.setText("Aucun jeu actif.");
            return;
        }
        currentGame.reset();
        lblStatus.setText(currentGame.statusText());
        if (!gameHost.getChildren().isEmpty()) {
            Node gameView = gameHost.getChildren().get(0);
            Platform.runLater(gameView::requestFocus);
        }
    }

    @FXML
    private void onBack() {
        if (currentGame != null) {
            currentGame.stop();
        }
        if (statusTicker != null) {
            statusTicker.stop();
        }
        if (onBack != null) {
            onBack.run();
        }
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

