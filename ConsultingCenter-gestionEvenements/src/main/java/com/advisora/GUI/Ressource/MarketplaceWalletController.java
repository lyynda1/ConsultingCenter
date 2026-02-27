package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.WalletTopup;
import com.advisora.Services.ressource.WalletService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class MarketplaceWalletController {
    @FXML private Label lblWalletBalance;
    @FXML private ComboBox<String> comboProvider;
    @FXML private TextField txtAmountMoney;
    @FXML private Label lblCoinPreview;
    @FXML private Label lblLastPaymentId;
    @FXML private Label lblCoinsAdded;
    @FXML private Label lblBalanceBefore;
    @FXML private Label lblBalanceAfter;
    @FXML private Label lblPaymentInfo;
    @FXML private ListView<WalletTopup> listTopups;
    @FXML private Label lblStatus;

    private final WalletService walletService = new WalletService();
    private final ObservableList<WalletTopup> topupData = FXCollections.observableArrayList();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private String lastPaymentUrl;

    @FXML
    public void initialize() {
        try {
            authorizeClient();

            comboProvider.setItems(FXCollections.observableArrayList("FLOUCI", "D17", "STRIPE"));
            comboProvider.getSelectionModel().select("FLOUCI");

            listTopups.setItems(topupData);
            listTopups.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(WalletTopup item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildTopupCard(item));
                }
            });

            listTopups.getSelectionModel().selectedItemProperty().addListener((obs, oldV, topup) -> {
                if (topup == null) {
                    return;
                }
                lastPaymentUrl = safe(topup.getPaymentUrl());
                if (lblLastPaymentId != null) {
                    lblLastPaymentId.setText(resolvePaymentId(topup));
                }
            });

            txtAmountMoney.textProperty().addListener((obs, oldV, newV) -> refreshCoinPreview());

            refreshAll();
        } catch (Exception ex) {
            if (lblStatus != null) {
                lblStatus.setText("Erreur wallet: " + ex.getMessage());
            }
        }
    }

    @FXML
    private void onRefresh() {
        refreshAll();
    }

    @FXML
    private void onCreatePayment() {
        try {
            authorizeClient();
            double amount = parsePositiveAmount(txtAmountMoney.getText(), "Montant invalide.");
            String provider = comboProvider.getValue();

            int userId = SessionContext.getCurrentUserId();
            double before = walletService.getBalanceCoins(userId);
            WalletTopup topup = walletService.createTopupRequest(userId, provider, amount);
            lastPaymentUrl = safe(topup.getPaymentUrl());
            String paymentId = resolvePaymentId(topup);

            if (lastPaymentUrl != null && !lastPaymentUrl.isBlank()) {
                openInAppCheckout(lastPaymentUrl);
            }

            boolean coinsAdded = false;
            double after;
            String resultMessage;
            try {
                WalletTopup confirmed = walletService.confirmTopup(userId, topup.getIdTopup());
                after = walletService.getBalanceCoins(userId);
                coinsAdded = isPaidOrSucceeded(confirmed.getStatus()) && (after > before + 1e-9);
                resultMessage = "Paiement confirme (" + safe(confirmed.getStatus()) + ").";
            } catch (Exception confirmEx) {
                after = walletService.getBalanceCoins(userId);
                resultMessage = "Paiement non confirme: " + confirmEx.getMessage();
            }

            setPaymentResult(paymentId, coinsAdded, before, after, resultMessage);
            refreshAll();
        } catch (Exception ex) {
            showError("Recharge wallet", ex.getMessage());
        }
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) (lblStatus != null ? lblStatus.getScene().getWindow() : null);
        if (stage != null) {
            stage.close();
        }
    }

    private void refreshAll() {
        int userId = SessionContext.getCurrentUserId();
        double balance = walletService.getBalanceCoins(userId);
        if (lblWalletBalance != null) {
            lblWalletBalance.setText(String.format(Locale.ROOT, "%.3f coins", balance));
        }
        List<WalletTopup> topups = walletService.listTopupsForUser(userId, 20);
        topupData.setAll(topups);
        refreshCoinPreview();
    }

    private void refreshCoinPreview() {
        double amount = 0.0;
        try {
            if (txtAmountMoney != null && txtAmountMoney.getText() != null && !txtAmountMoney.getText().isBlank()) {
                amount = Double.parseDouble(txtAmountMoney.getText().trim());
            }
        } catch (NumberFormatException ignored) {
            amount = 0.0;
        }
        double coins = walletService.estimateCoins(amount);
        if (lblCoinPreview != null) {
            lblCoinPreview.setText(formatCoins(coins));
        }
    }

    private double parsePositiveAmount(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            double amount = Double.parseDouble(value.trim());
            if (amount <= 0) {
                throw new IllegalArgumentException(message);
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private void authorizeClient() {
        if (SessionContext.getCurrentRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Seul le CLIENT peut utiliser le wallet.");
        }
    }

    private String formatMoney(double amount) {
        return String.format(Locale.ROOT, "%.3f TND", amount);
    }

    private String formatCoins(double amount) {
        return String.format(Locale.ROOT, "◎ %.3f coins", amount);
    }

    private VBox buildTopupCard(WalletTopup topup) {
        Label title = new Label("#" + topup.getIdTopup() + " - " + safe(topup.getProvider()));
        title.getStyleClass().add("card-title");

        Label status = new Label(safe(topup.getStatus()));
        status.getStyleClass().add("status-badge");
        status.getStyleClass().add(statusClass(safe(topup.getStatus())));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, status);

        Label row1 = new Label("Montant: " + formatMoney(topup.getAmountMoney())
                + "   |   Coins: " + formatCoins(topup.getCoinAmount()));
        row1.getStyleClass().add("card-meta");

        Label row2 = new Label("Payment ID: " + resolvePaymentId(topup));
        row2.getStyleClass().add("card-meta");

        String createdAt = formatTimestamp(topup.getCreatedAt());
        String confirmedAt = formatTimestamp(topup.getConfirmedAt());
        Label row3 = new Label("Cree le: " + createdAt + "   |   Confirme le: " + confirmedAt);
        row3.getStyleClass().add("card-meta");

        VBox card = new VBox(8, head, row1, row2, row3);
        if (topup.getNote() != null && !topup.getNote().isBlank()) {
            Label note = new Label("Info: " + topup.getNote().trim());
            note.getStyleClass().add("card-meta");
            note.setWrapText(true);
            card.getChildren().add(note);
        }
        card.getStyleClass().addAll("resource-card", "payment-card");
        return card;
    }

    private void setPaymentResult(String paymentId, boolean coinsAdded, double before, double after, String message) {
        if (lblLastPaymentId != null) {
            lblLastPaymentId.setText(paymentId == null || paymentId.isBlank() ? "-" : paymentId);
        }
        if (lblCoinsAdded != null) {
            lblCoinsAdded.setText(coinsAdded ? "OUI" : "NON");
        }
        if (lblBalanceBefore != null) {
            lblBalanceBefore.setText(formatCoins(before));
        }
        if (lblBalanceAfter != null) {
            lblBalanceAfter.setText(formatCoins(after));
        }
        if (lblPaymentInfo != null) {
            lblPaymentInfo.setText(message == null ? "" : message);
        }
        if (lblStatus != null) {
            lblStatus.setText("Coins ajoutes au wallet : " + (coinsAdded ? "OUI" : "NON"));
        }
    }

    private boolean isPaidOrSucceeded(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        return "PAID".equals(s) || "SUCCEEDED".equals(s);
    }

    private String resolvePaymentId(WalletTopup topup) {
        if (topup == null) {
            return "-";
        }
        String external = safe(topup.getExternalRef());
        if (!external.isBlank()) {
            return external;
        }
        return "TOPUP#" + topup.getIdTopup();
    }

    private String formatTimestamp(Timestamp ts) {
        if (ts == null) {
            return "-";
        }
        try {
            return ts.toLocalDateTime().format(TS_FMT);
        } catch (Exception ignored) {
            return ts.toString();
        }
    }

    private String statusClass(String status) {
        if (status == null) {
            return "status-pending";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("PAID".equals(normalized) || "SUCCEEDED".equals(normalized) || "SUCCESS".equals(normalized)) {
            return "status-accepted";
        }
        if ("FAILED".equals(normalized) || "CANCELLED".equals(normalized) || "ERROR".equals(normalized)) {
            return "status-refused";
        }
        return "status-pending";
    }

    private void openInAppCheckout(String paymentUrl) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/MarketplacePaymentWebView.fxml"));
        Parent root = loader.load();
        MarketplacePaymentWebController controller = loader.getController();
        controller.setPaymentUrl(paymentUrl);

        Stage stage = new Stage();
        Window owner = lblStatus != null && lblStatus.getScene() != null ? lblStatus.getScene().getWindow() : null;
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Paiement In-App");
        stage.setScene(new Scene(root));
        stage.setMinWidth(1000);
        stage.setMinHeight(760);
        stage.showAndWait();
    }

    private void showError(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
