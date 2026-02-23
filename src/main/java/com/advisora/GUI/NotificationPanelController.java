package com.advisora.GUI;

import com.advisora.Model.strategie.Notification;
import com.advisora.Services.strategie.NotificationManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class NotificationPanelController implements Initializable {

    @FXML private HBox notificationPanelRoot;
    @FXML private VBox notificationsContainer;
    @FXML private Circle notificationBadge;
    @FXML private Label notifCount;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadNotifications();
    }

    private void loadNotifications() {
        notificationsContainer.getChildren().clear();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Notification notification : NotificationManager.getInstance().getNotifications()) {
            Label titleLabel = new Label(notification.getTitle());
            titleLabel.getStyleClass().add("notification-title");

            Text messageText = new Text(notification.getMessage());
            messageText.getStyleClass().add("notification-message");
            messageText.setWrappingWidth(230);

            Text timestampText = new Text(notification.getTimestamp().format(fmt));
            timestampText.getStyleClass().add("notification-timestamp");

            TextFlow textFlow = new TextFlow(messageText, new Text("\n"), timestampText);

            VBox notificationBox = new VBox(3, titleLabel, textFlow);
            notificationBox.getStyleClass().add("notification-item");

            notificationBox.setOnMouseClicked(e -> {
                NotificationManager.getInstance().markAsRead(notification);
                NotificationManager.getInstance().notifyChanged(); // ✅ important
                loadNotifications();
            });

            notificationsContainer.getChildren().add(notificationBox);
        }
    }

    @FXML private Label dragHandle; // optional, but fine

    private double pressSceneX;
    private double pressSceneY;
    private double startTranslateX;
    private double startTranslateY;

    @FXML
    private void handleMousePressed(MouseEvent event) {
        pressSceneX = event.getSceneX();
        pressSceneY = event.getSceneY();

        startTranslateX = notificationPanelRoot.getTranslateX();
        startTranslateY = notificationPanelRoot.getTranslateY();

        event.consume();
    }

    @FXML
    private void handleMouseDragged(MouseEvent event) {
        double dx = event.getSceneX() - pressSceneX;
        double dy = event.getSceneY() - pressSceneY;

        notificationPanelRoot.setTranslateX(startTranslateX + dx);
        notificationPanelRoot.setTranslateY(startTranslateY + dy);

        event.consume();
    }

    @FXML
    private void handleClearAll(ActionEvent e) {
        if (NotificationManager.getInstance().getNotifications().isEmpty()) {
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Supprimer toutes les notifications ?",
                ButtonType.OK,
                ButtonType.CANCEL
        );
        confirm.setHeaderText("Confirmation");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        NotificationManager.getInstance().clearAllNotifications(); // DB + list
        loadNotifications(); // refresh UI
    }



}





