package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.ResourceMarketListing;
import com.advisora.Model.ressource.WalletTopup;
import com.advisora.Services.ressource.ResourceMarketplaceService;
import com.advisora.Services.ressource.WalletService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Locale;

public class MarketplaceCheckoutController {
    @FXML private Label lblResourceName;
    @FXML private Label lblSeller;
    @FXML private Label lblQty;
    @FXML private Label lblUnitPrice;
    @FXML private Label lblTotal;
    @FXML private Label lblStatus;
    @FXML private ComboBox<String> comboPaymentProvider;

    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtCity;
    @FXML private TextField txtAddress;
    @FXML private TextField txtPhone;
    @FXML private TextField txtPhone2;

    private final ResourceMarketplaceService marketplaceService = new ResourceMarketplaceService();
    private final WalletService walletService = new WalletService();

    private ResourceMarketListing listing;
    private int quantity;
    private boolean checkoutCompleted;
    private String checkoutStatusMessage = "";
    private int checkoutOrderId;

    @FXML
    public void initialize() {
        if (comboPaymentProvider != null) {
            comboPaymentProvider.getItems().setAll("STRIPE", "FLOUCI", "D17");
            comboPaymentProvider.getSelectionModel().select("STRIPE");
        }
        if (lblStatus != null) {
            lblStatus.setText("Completez les infos livraison puis confirmez (paiement in-app automatique).");
        }
    }

    public void setContext(ResourceMarketListing listing, int quantity) {
        this.listing = listing;
        this.quantity = quantity;
        refreshSummary();
    }

    @FXML
    private void onConfirmCheckout() {
        try {
            authorizeClient();
            if (listing == null) {
                throw new IllegalStateException("Annonce invalide.");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantite invalide.");
            }

            ResourceMarketplaceService.CheckoutRequest checkout = new ResourceMarketplaceService.CheckoutRequest();
            checkout.setFirstName(required(txtFirstName, "Nom obligatoire."));
            checkout.setLastName(required(txtLastName, "Prenom obligatoire."));
            checkout.setCity(required(txtCity, "Ville obligatoire."));
            checkout.setAddressLine(required(txtAddress, "Adresse complete obligatoire."));
            checkout.setPhone(optional(txtPhone));
            checkout.setPhone2(optional(txtPhone2));

            ensurePaymentAutomatically();

            ResourceMarketplaceService.CheckoutResult result = marketplaceService.checkoutAndBuyListing(
                    SessionContext.getCurrentUserId(),
                    listing.getIdListing(),
                    quantity,
                    null,
                    checkout
            );

            checkoutCompleted = true;
            checkoutOrderId = result.getOrderId();
            checkoutStatusMessage = formatSuccessMessage(result);
            if (lblStatus != null) {
                lblStatus.setText(checkoutStatusMessage);
            }
            Alert ok = new Alert(Alert.AlertType.INFORMATION, checkoutStatusMessage, ButtonType.OK);
            ok.setHeaderText("Commande confirmee");
            ok.showAndWait();
            closeWindow();
        } catch (Exception ex) {
            showError("Checkout", ex.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    public boolean isCheckoutCompleted() {
        return checkoutCompleted;
    }

    public String getCheckoutStatusMessage() {
        return checkoutStatusMessage == null || checkoutStatusMessage.isBlank()
                ? "Commande creee."
                : checkoutStatusMessage;
    }

    public int getCheckoutOrderId() {
        return checkoutOrderId;
    }

    private void ensurePaymentAutomatically() throws Exception {
        int userId = SessionContext.getCurrentUserId();
        double totalCoins = Math.max(0.001, listing.getUnitPrice() * quantity);
        double balanceCoins = walletService.getBalanceCoins(userId);
        double deficitCoins = round3(totalCoins - balanceCoins);
        if (deficitCoins <= 0) {
            if (lblStatus != null) {
                lblStatus.setText("Solde suffisant. Creation commande en cours...");
            }
            return;
        }

        String provider = comboPaymentProvider == null || comboPaymentProvider.getValue() == null
                ? "STRIPE"
                : comboPaymentProvider.getValue().trim();
        double coinRate = walletService.estimateCoins(1.0);
        if (coinRate <= 0) {
            coinRate = 10.0;
        }
        double amountMoney = round3(deficitCoins / coinRate);
        amountMoney = Math.max(amountMoney, 0.001);

        if (lblStatus != null) {
            lblStatus.setText("Paiement in-app en cours (" + provider + ")...");
        }
        WalletTopup topup = walletService.createTopupRequest(userId, provider, amountMoney);
        if (topup.getPaymentUrl() != null && !topup.getPaymentUrl().isBlank()) {
            openInAppCheckout(topup.getPaymentUrl().trim());
        }
        walletService.confirmTopup(userId, topup.getIdTopup());
        if (lblStatus != null) {
            lblStatus.setText("Paiement confirme. Finalisation commande...");
        }
    }

    private void refreshSummary() {
        if (listing == null) {
            return;
        }
        if (lblResourceName != null) {
            lblResourceName.setText(safe(listing.getResourceName()));
        }
        if (lblSeller != null) {
            lblSeller.setText(safe(listing.getSellerName()));
        }
        if (lblQty != null) {
            lblQty.setText(String.valueOf(quantity));
        }
        if (lblUnitPrice != null) {
            lblUnitPrice.setText(formatCoins(listing.getUnitPrice()));
        }
        if (lblTotal != null) {
            lblTotal.setText(formatCoins(listing.getUnitPrice() * quantity));
        }
    }

    private String formatSuccessMessage(ResourceMarketplaceService.CheckoutResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Commande #").append(result.getOrderId())
                .append(" creee. Livraison: ")
                .append(safe(result.getDeliveryStatus()));
        if (result.getTrackingCode() != null && !result.getTrackingCode().isBlank()) {
            sb.append(" | Tracking: ").append(result.getTrackingCode().trim());
        }
        if (result.getDeliveryMessage() != null && !result.getDeliveryMessage().isBlank()) {
            sb.append(" | ").append(result.getDeliveryMessage().trim());
        }
        return sb.toString();
    }

    private String required(TextField tf, String msg) {
        String value = optional(tf);
        if (value.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
        return value;
    }

    private String optional(TextField tf) {
        return tf == null || tf.getText() == null ? "" : tf.getText().trim();
    }

    private void closeWindow() {
        Stage stage = lblStatus == null || lblStatus.getScene() == null ? null : (Stage) lblStatus.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private void authorizeClient() {
        if (SessionContext.getCurrentRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Seul le CLIENT peut valider ce checkout.");
        }
    }

    private String formatCoins(double value) {
        return String.format(Locale.ROOT, "◎ %.3f coins", value);
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
