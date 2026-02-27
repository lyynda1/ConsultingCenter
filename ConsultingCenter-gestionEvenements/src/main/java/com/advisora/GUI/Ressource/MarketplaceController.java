package com.advisora.GUI.Ressource;

import com.advisora.Model.ressource.ResourceMarketListing;
import com.advisora.Model.ressource.ResourceMarketOrder;
import com.advisora.Model.ressource.ResourceMarketReview;
import com.advisora.Services.ressource.ResourceMarketplaceService;
import com.advisora.Services.ressource.WalletService;
import com.advisora.Services.user.SessionContext;
import com.advisora.enums.UserRole;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketplaceController {
    @FXML private TextField txtBuySearch;
    @FXML private TextField txtBuyQuantity;
    @FXML private ListView<ResourceMarketListing> listMarketBuy;
    @FXML private Label lblMarketListings;
    @FXML private Label lblWalletBalance;
    @FXML private Button btnBuyListing;
    @FXML private Label lblBuyRule;

    @FXML private TabPane tabShop;
    @FXML private ListView<ResourceMarketOrder> listMyMarketOrders;
    @FXML private Label lblMyOrdersCount;
    @FXML private Button btnStar1;
    @FXML private Button btnStar2;
    @FXML private Button btnStar3;
    @FXML private Button btnStar4;
    @FXML private Button btnStar5;
    @FXML private Label lblReviewStarsValue;
    @FXML private TextArea txtReviewComment;
    @FXML private Button btnSubmitReview;
    @FXML private Label lblReviewRule;
    @FXML private Label lblStatus;

    private final ResourceMarketplaceService marketplaceService = new ResourceMarketplaceService();
    private final WalletService walletService = new WalletService();

    private final ObservableList<ResourceMarketListing> allMarketBuyData = FXCollections.observableArrayList();
    private final ObservableList<ResourceMarketListing> marketBuyData = FXCollections.observableArrayList();
    private final ObservableList<ResourceMarketOrder> myOrdersData = FXCollections.observableArrayList();
    private final Map<String, Image> imageCache = new HashMap<>();
    private final Map<Integer, List<ResourceMarketReview>> recentReviewsByListing = new HashMap<>();
    private Timeline marketAutoRefreshTimeline;
    private String lastMarketSnapshot = "";
    private String lastOrdersSnapshot = "";
    private double currentWalletBalance;
    private int selectedReviewStars = 5;

    @FXML
    public void initialize() {
        try {
            authorizeClient();

            listMarketBuy.setItems(marketBuyData);
            listMarketBuy.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(ResourceMarketListing item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildShopProductCard(item));
                }
            });

            listMyMarketOrders.setItems(myOrdersData);
            listMyMarketOrders.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(ResourceMarketOrder item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(null);
                    setGraphic(buildMarketOrderCard(item));
                }
            });
            listMyMarketOrders.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updateReviewActionState(newV));

            setupReviewStarsUi();

            if (txtBuySearch != null) {
                txtBuySearch.textProperty().addListener((obs, oldV, newV) -> applyBuyFilter(newV));
            }
            if (txtBuyQuantity != null) {
                txtBuyQuantity.textProperty().addListener((obs, oldV, newV) ->
                        updateBuyActionState(listMarketBuy == null ? null : listMarketBuy.getSelectionModel().getSelectedItem()));
            }
            listMarketBuy.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updateBuyActionState(newV));

            refreshAll();
            updateBuyActionState(null);
            updateReviewActionState(null);
            startAutoRefresh();
            bindWindowAutoStop();
        } catch (Exception ex) {
            if (lblStatus != null) {
                lblStatus.setText("Erreur chargement mini shop: " + ex.getMessage());
            }
        }
    }

    @FXML
    private void onRefresh() {
        refreshAll();
    }

    @FXML
    private void onOpenSellWindow() {
        try {
            authorizeClient();
            openModal("/views/resource/MarketplaceSellView.fxml", "Publier une annonce");
            refreshAll();
        } catch (Exception ex) {
            showError("Marketplace", ex.getMessage());
        }
    }

    @FXML
    private void onOpenMyListingsWindow() {
        try {
            authorizeClient();
            openModal("/views/resource/MarketplaceMyListingsView.fxml", "Mes annonces");
            refreshAll();
        } catch (Exception ex) {
            showError("Marketplace", ex.getMessage());
        }
    }

    @FXML
    private void onOpenWalletWindow() {
        try {
            authorizeClient();
            openModal("/views/resource/MarketplaceWalletView.fxml", "Wallet");
            refreshAll();
        } catch (Exception ex) {
            showError("Wallet", ex.getMessage());
        }
    }

    @FXML
    private void onBuyListing() {
        try {
            authorizeClient();
            ResourceMarketListing selected = requireBuyListingSelection();
            if (isOwnListing(selected)) {
                throw new IllegalStateException("Vous ne pouvez pas acheter votre propre annonce.");
            }
            int qty = parsePositiveQuantity(txtBuyQuantity.getText(), "Quantite achat invalide.");
            openCheckoutWindow(selected, qty);
        } catch (Exception ex) {
            showError("Marketplace", ex.getMessage());
        }
    }

    @FXML
    private void onSubmitReview() {
        try {
            authorizeClient();
            ResourceMarketOrder selectedOrder = requireReviewOrderSelection();
            if (!isReviewableOrder(selectedOrder)) {
                throw new IllegalStateException("Avis disponible uniquement pour une commande CONFIRMED.");
            }

            int starsValue = selectedReviewStars;
            if (starsValue < 1 || starsValue > 5) {
                throw new IllegalArgumentException("Selectionnez une note entre 1 et 5.");
            }
            String comment = txtReviewComment == null ? null : txtReviewComment.getText();

            marketplaceService.addOrUpdateReviewForOrder(
                    SessionContext.getCurrentUserId(),
                    selectedOrder.getIdOrder(),
                    starsValue,
                    comment
            );
            if (lblStatus != null) {
                lblStatus.setText("Avis enregistre avec succes.");
            }
            if (lblReviewRule != null) {
                lblReviewRule.setText("Avis enregistre.");
            }

            int selectedOrderId = selectedOrder.getIdOrder();
            if (txtReviewComment != null) {
                txtReviewComment.clear();
            }
            refreshAll();
            restoreOrderSelection(selectedOrderId);
            updateReviewActionState(listMyMarketOrders == null ? null : listMyMarketOrders.getSelectionModel().getSelectedItem());
        } catch (Exception ex) {
            showError("Avis client", ex.getMessage());
        }
    }

    @FXML
    public void refreshAll() {
        refreshMarket();
        refreshWalletBalance();
    }

    private void refreshMarket() {
        int clientId = SessionContext.getCurrentUserId();
        List<ResourceMarketListing> marketListings = marketplaceService.listActiveListingsForBuyer(clientId);
        List<ResourceMarketOrder> myOrders = marketplaceService.listBuyerOrders(clientId);
        List<Integer> listingIds = marketListings.stream().map(ResourceMarketListing::getIdListing).toList();

        int selectedListingId = -1;
        ResourceMarketListing selected = listMarketBuy == null ? null : listMarketBuy.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedListingId = selected.getIdListing();
        }
        int selectedOrderId = -1;
        ResourceMarketOrder selectedOrder = listMyMarketOrders == null ? null : listMyMarketOrders.getSelectionModel().getSelectedItem();
        if (selectedOrder != null) {
            selectedOrderId = selectedOrder.getIdOrder();
        }

        recentReviewsByListing.clear();
        recentReviewsByListing.putAll(marketplaceService.listRecentReviewsByListingIds(listingIds, 3));

        String marketSnapshot = buildMarketSnapshot(marketListings);
        if (!marketSnapshot.equals(lastMarketSnapshot)) {
            lastMarketSnapshot = marketSnapshot;
            allMarketBuyData.setAll(marketListings);
            applyBuyFilter(txtBuySearch == null ? null : txtBuySearch.getText());
            restoreSelection(selectedListingId);
        }
        updateBuyActionState(listMarketBuy == null ? null : listMarketBuy.getSelectionModel().getSelectedItem());

        String ordersSnapshot = buildOrdersSnapshot(myOrders);
        if (!ordersSnapshot.equals(lastOrdersSnapshot)) {
            lastOrdersSnapshot = ordersSnapshot;
            myOrdersData.setAll(myOrders);
            restoreOrderSelection(selectedOrderId);
            if (lblMyOrdersCount != null) {
                lblMyOrdersCount.setText(String.valueOf(myOrders.size()));
            }
        }
        updateReviewActionState(listMyMarketOrders == null ? null : listMyMarketOrders.getSelectionModel().getSelectedItem());
    }

    private void refreshWalletBalance() {
        currentWalletBalance = walletService.getBalanceCoins(SessionContext.getCurrentUserId());
        if (lblWalletBalance != null) {
            lblWalletBalance.setText(String.format(Locale.ROOT, "%.3f coins", currentWalletBalance));
        }
    }

    private void applyBuyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ResourceMarketListing> filtered = allMarketBuyData.stream()
                .filter(l -> q.isEmpty()
                        || safe(l.getResourceName()).toLowerCase(Locale.ROOT).contains(q)
                        || safe(l.getFournisseurName()).toLowerCase(Locale.ROOT).contains(q)
                        || safe(l.getSellerName()).toLowerCase(Locale.ROOT).contains(q))
                .toList();
        marketBuyData.setAll(filtered);
        if (lblMarketListings != null) {
            lblMarketListings.setText(String.valueOf(filtered.size()));
        }
    }

    private VBox buildShopProductCard(ResourceMarketListing listing) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(220);
        imageView.setFitHeight(130);
        imageView.setPreserveRatio(false);
        imageView.getStyleClass().add("shop-image");
        imageView.setImage(resolveImage(listing));

        Label title = new Label(safe(listing.getResourceName()));
        title.getStyleClass().add("shop-title");
        HBox titleRow = new HBox(8, title);
        if (isOwnListing(listing)) {
            Label ownBadge = new Label("Votre annonce");
            ownBadge.getStyleClass().add("status-badge");
            ownBadge.getStyleClass().add("status-pending");
            titleRow.getChildren().add(ownBadge);
        }

        Label seller = new Label("Vendeur: " + safe(listing.getSellerName()));
        seller.getStyleClass().add("card-meta");

        Label stock = new Label("Disponible: " + listing.getQtyAvailable() + "  |  Prix: " + formatCoins(listing.getUnitPrice()));
        stock.getStyleClass().add("card-meta");

        Label rating = new Label(renderStars(listing.getAverageStars()) + "  " + formatRating(listing));
        rating.getStyleClass().add("shop-stars");

        Label fournisseur = new Label("Fournisseur: " + safe(listing.getFournisseurName()));
        fournisseur.getStyleClass().add("card-meta");

        VBox content = new VBox(5, titleRow, seller, stock, rating, fournisseur);
        HBox row = new HBox(12, imageView, content);
        HBox.setHgrow(content, Priority.ALWAYS);

        VBox card = new VBox(8, row);
        if (listing.getNote() != null && !listing.getNote().isBlank()) {
            Label note = new Label("Description: " + listing.getNote().trim());
            note.getStyleClass().add("card-meta");
            note.setWrapText(true);
            card.getChildren().add(note);
        }
        List<ResourceMarketReview> reviews = recentReviewsByListing.get(listing.getIdListing());
        if (reviews != null && !reviews.isEmpty()) {
            Label reviewTitle = new Label("Avis clients");
            reviewTitle.getStyleClass().add("review-title");
            card.getChildren().add(reviewTitle);

            VBox reviewsBox = new VBox(4);
            for (ResourceMarketReview review : reviews) {
                String comment = safe(review.getComment());
                if (comment.isBlank()) {
                    comment = "Sans commentaire";
                }
                String line = renderStars(review.getStars())
                        + " " + safe(review.getReviewerName())
                        + " - " + comment;
                Label reviewLine = new Label(line);
                reviewLine.setWrapText(true);
                reviewLine.getStyleClass().add("review-item");
                reviewsBox.getChildren().add(reviewLine);
            }
            card.getChildren().add(reviewsBox);
        }
        card.getStyleClass().addAll("resource-card", "shop-card");
        return card;
    }

    private VBox buildMarketOrderCard(ResourceMarketOrder order) {
        Label title = new Label("#" + order.getIdOrder() + " - " + safe(order.getResourceName()));
        title.getStyleClass().add("card-title");

        Label badge = new Label(order.getStatus() == null ? "-" : order.getStatus());
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(marketStatusClass(order.getStatus()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, title, spacer, badge);

        Label line1 = new Label(
                "Qte: " + order.getQty()
                        + " | PU: " + formatCoins(order.getUnitPrice())
                        + " | Total: " + formatCoins(order.getTotalAmount())
        );
        line1.getStyleClass().add("card-meta");

        Label line2 = new Label("Vendeur: " + safe(order.getSellerName()));
        line2.getStyleClass().add("card-meta");
        Label line3 = new Label("Livraison: " + normalizeDeliveryStatus(order.getDeliveryStatus()));
        line3.getStyleClass().add("card-meta");
        String reviewLineText = hasReview(order)
                ? ("Avis: " + renderStars(order.getReviewStars()) + " (deja donne)")
                : "Avis: non donne";
        Label line4 = new Label(reviewLineText);
        line4.getStyleClass().add("card-meta");
        if (order.getDeliveryTrackingCode() != null && !order.getDeliveryTrackingCode().isBlank()) {
            Label track = new Label("Tracking: " + order.getDeliveryTrackingCode().trim());
            track.getStyleClass().add("card-meta");
            VBox card = new VBox(8, head, line1, line2, line3, line4, track);
            card.getStyleClass().addAll("resource-card", "reservation-card");
            return card;
        }
        VBox card = new VBox(8, head, line1, line2, line3, line4);
        card.getStyleClass().addAll("resource-card", "reservation-card");
        return card;
    }

    private String resolveImageUrl(ResourceMarketListing listing) {
        String direct = safe(listing.getImageUrl());
        if (!direct.isBlank()) {
            return direct;
        }
        String fallbackKey = safe(listing.getResourceName());
        return "https://loremflickr.com/640/360/" + fallbackKey.replace(" ", ",") + "?lock=" + listing.getIdListing();
    }

    private String marketStatusClass(String status) {
        if (status == null) {
            return "status-pending";
        }
        if ("ACTIVE".equalsIgnoreCase(status)
                || "CONFIRMED".equalsIgnoreCase(status)
                || "OPEN".equalsIgnoreCase(status)
                || "LISTED".equalsIgnoreCase(status)) {
            return "status-accepted";
        }
        if ("SOLD_OUT".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            return "status-refused";
        }
        return "status-pending";
    }

    private String renderStars(double average) {
        int full = (int) Math.round(Math.max(0, Math.min(5, average)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < full ? '\u2605' : '\u2606');
        }
        return sb.toString();
    }

    private String renderStars(int stars) {
        int full = Math.max(0, Math.min(5, stars));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < full ? '\u2605' : '\u2606');
        }
        return sb.toString();
    }

    private String formatRating(ResourceMarketListing listing) {
        if (listing.getReviewCount() <= 0) {
            return "Nouveau";
        }
        return String.format(Locale.ROOT, "%.1f (%d avis)", listing.getAverageStars(), listing.getReviewCount());
    }

    private String formatCoins(double value) {
        return String.format(Locale.ROOT, "◎ %.3f coins", value);
    }

    private ResourceMarketListing requireBuyListingSelection() {
        ResourceMarketListing selected = listMarketBuy.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez une annonce a acheter.");
        }
        return selected;
    }

    private ResourceMarketOrder requireReviewOrderSelection() {
        if (listMyMarketOrders == null) {
            throw new IllegalArgumentException("Selectionnez un achat.");
        }
        ResourceMarketOrder selected = listMyMarketOrders.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException("Selectionnez un achat.");
        }
        return selected;
    }

    private int parsePositiveQuantity(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            int qty = Integer.parseInt(value.trim());
            if (qty <= 0) {
                throw new IllegalArgumentException(message);
            }
            return qty;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private void authorizeClient() {
        if (SessionContext.getCurrentRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Seul le CLIENT peut utiliser cette vue.");
        }
    }

    private void showError(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(header);
        a.showAndWait();
    }

    private void openModal(String fxmlPath, String title) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();
        Stage stage = new Stage();
        Window owner = listMarketBuy != null && listMarketBuy.getScene() != null ? listMarketBuy.getScene().getWindow() : null;
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private void openCheckoutWindow(ResourceMarketListing listing, int qty) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/resource/MarketplaceCheckoutView.fxml"));
        Parent root = loader.load();

        MarketplaceCheckoutController controller = loader.getController();
        controller.setContext(listing, qty);

        Stage stage = new Stage();
        Window owner = listMarketBuy != null && listMarketBuy.getScene() != null ? listMarketBuy.getScene().getWindow() : null;
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Checkout + Livraison");
        stage.setScene(new Scene(root));
        stage.showAndWait();

        if (controller.isCheckoutCompleted()) {
            if (lblStatus != null) {
                lblStatus.setText(controller.getCheckoutStatusMessage());
            }
            int createdOrderId = controller.getCheckoutOrderId();
            if (txtBuyQuantity != null) {
                txtBuyQuantity.clear();
            }
            refreshAll();
            if (createdOrderId > 0) {
                restoreOrderSelection(createdOrderId);
                promptReviewAfterCheckout(createdOrderId);
            }
        }
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        marketAutoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            try {
                refreshAll();
            } catch (Exception ex) {
                if (lblStatus != null) {
                    lblStatus.setText("Sync shop: " + ex.getMessage());
                }
            }
        }));
        marketAutoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        marketAutoRefreshTimeline.play();
    }

    private void bindWindowAutoStop() {
        if (listMarketBuy == null) {
            return;
        }
        listMarketBuy.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            if (newScene.getWindow() != null) {
                newScene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> stopAutoRefresh());
                return;
            }
            newScene.windowProperty().addListener((wObs, oldWindow, newWindow) -> {
                if (newWindow != null) {
                    newWindow.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> stopAutoRefresh());
                }
            });
        });
    }

    private void stopAutoRefresh() {
        if (marketAutoRefreshTimeline != null) {
            marketAutoRefreshTimeline.stop();
            marketAutoRefreshTimeline = null;
        }
    }

    private void updateBuyActionState(ResourceMarketListing selected) {
        if (btnBuyListing == null) {
            return;
        }
        if (selected == null) {
            btnBuyListing.setDisable(true);
            if (lblBuyRule != null) {
                lblBuyRule.setText("Selectionnez une annonce.");
            }
            return;
        }

        if (isOwnListing(selected)) {
            btnBuyListing.setDisable(true);
            if (lblBuyRule != null) {
                lblBuyRule.setText("Votre annonce: achat indisponible.");
            }
            return;
        }

        int qty = parseQuantityForUi();
        double total = qty * selected.getUnitPrice();
        if (qty <= 0) {
            btnBuyListing.setDisable(true);
            if (lblBuyRule != null) {
                lblBuyRule.setText("Quantite invalide.");
            }
            return;
        }
        if (currentWalletBalance + 1e-9 < total) {
            btnBuyListing.setDisable(false);
            if (lblBuyRule != null) {
                lblBuyRule.setText("Solde insuffisant (" + formatCoins(currentWalletBalance) + "). Paiement in-app auto au checkout.");
            }
            return;
        }

        btnBuyListing.setDisable(false);
        if (lblBuyRule != null) {
            lblBuyRule.setText("Total: " + formatCoins(total));
        }
    }

    private boolean isOwnListing(ResourceMarketListing listing) {
        return listing != null && listing.getSellerId() == SessionContext.getCurrentUserId();
    }

    private Image resolveImage(ResourceMarketListing listing) {
        String url = resolveImageUrl(listing);
        String key = listing.getIdListing() + "|" + safe(url);
        Image cached = imageCache.get(key);
        if (cached != null) {
            return cached;
        }
        Image img = new Image(url, 220, 130, false, true, true);
        imageCache.put(key, img);
        return img;
    }

    private String buildMarketSnapshot(List<ResourceMarketListing> list) {
        StringBuilder sb = new StringBuilder();
        for (ResourceMarketListing l : list) {
            sb.append(l.getIdListing()).append('|')
                    .append(safe(l.getStatus())).append('|')
                    .append(l.getQtyAvailable()).append('|')
                    .append(l.getUnitPrice()).append('|')
                    .append(String.format(Locale.ROOT, "%.2f", l.getAverageStars())).append('|')
                    .append(l.getReviewCount()).append(';');
        }
        return sb.toString();
    }

    private String buildOrdersSnapshot(List<ResourceMarketOrder> list) {
        StringBuilder sb = new StringBuilder();
        for (ResourceMarketOrder o : list) {
            sb.append(o.getIdOrder()).append('|')
                    .append(safe(o.getStatus())).append('|')
                    .append(o.getQty()).append('|')
                    .append(o.getTotalAmount()).append('|')
                    .append(o.getReviewStars()).append('|')
                    .append(safe(o.getReviewComment())).append(';');
        }
        return sb.toString();
    }

    private void restoreSelection(int idListing) {
        if (idListing <= 0 || listMarketBuy == null) {
            return;
        }
        for (ResourceMarketListing item : listMarketBuy.getItems()) {
            if (item.getIdListing() == idListing) {
                listMarketBuy.getSelectionModel().select(item);
                break;
            }
        }
    }

    private void restoreOrderSelection(int idOrder) {
        if (idOrder <= 0 || listMyMarketOrders == null) {
            return;
        }
        for (ResourceMarketOrder item : listMyMarketOrders.getItems()) {
            if (item.getIdOrder() == idOrder) {
                listMyMarketOrders.getSelectionModel().select(item);
                break;
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeDeliveryStatus(String status) {
        if (status == null || status.isBlank()) {
            return "EN_PREPARATION";
        }
        return status.trim();
    }

    private boolean isReviewableOrder(ResourceMarketOrder order) {
        return order != null && "CONFIRMED".equalsIgnoreCase(safe(order.getStatus()));
    }

    private boolean hasReview(ResourceMarketOrder order) {
        return order != null && order.getReviewStars() >= 1 && order.getReviewStars() <= 5;
    }

    private void updateReviewActionState(ResourceMarketOrder selected) {
        if (btnSubmitReview == null) {
            return;
        }
        boolean canReview = isReviewableOrder(selected);
        btnSubmitReview.setDisable(!canReview);
        setReviewStarsDisabled(!canReview);

        if (lblReviewRule == null) {
            return;
        }
        if (selected == null) {
            lblReviewRule.setText("Selectionnez une commande CONFIRMED.");
            setReviewStarsValue(5);
            if (txtReviewComment != null) {
                txtReviewComment.clear();
            }
            return;
        }
        if (canReview) {
            int stars = hasReview(selected) ? selected.getReviewStars() : 5;
            setReviewStarsValue(stars);
            if (txtReviewComment != null) {
                txtReviewComment.setText(hasReview(selected) ? safe(selected.getReviewComment()) : "");
            }
            lblReviewRule.setText(hasReview(selected)
                    ? "Avis deja donne. Vous pouvez le modifier et re-envoyer."
                    : "Vous pouvez noter cette ressource maintenant (1 a 5 etoiles).");
            return;
        }
        lblReviewRule.setText("Avis indisponible: statut " + safe(selected.getStatus()) + ".");
        if (txtReviewComment != null) {
            txtReviewComment.clear();
        }
    }

    private void promptReviewAfterCheckout(int orderId) {
        ResourceMarketOrder order = findOrderById(orderId);
        if (!isReviewableOrder(order) || hasReview(order)) {
            return;
        }

        Alert ask = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Commande confirmee. Voulez-vous laisser un avis maintenant ?",
                ButtonType.YES,
                ButtonType.NO
        );
        ask.setHeaderText("Noter votre achat");
        if (ask.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        if (tabShop != null && tabShop.getTabs().size() > 1) {
            tabShop.getSelectionModel().select(1);
        }
        restoreOrderSelection(orderId);
        setReviewStarsValue(5);
        if (txtReviewComment != null) {
            txtReviewComment.clear();
            txtReviewComment.requestFocus();
        }
        if (lblReviewRule != null) {
            lblReviewRule.setText("Donnez votre note puis cliquez Envoyer avis.");
        }
    }

    private ResourceMarketOrder findOrderById(int orderId) {
        if (orderId <= 0) {
            return null;
        }
        for (ResourceMarketOrder o : myOrdersData) {
            if (o.getIdOrder() == orderId) {
                return o;
            }
        }
        return null;
    }

    private int parseQuantityForUi() {
        if (txtBuyQuantity == null || txtBuyQuantity.getText() == null || txtBuyQuantity.getText().isBlank()) {
            return 1;
        }
        try {
            int qty = Integer.parseInt(txtBuyQuantity.getText().trim());
            return Math.max(0, qty);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @FXML
    private void onSelectReviewStars(ActionEvent event) {
        if (!(event.getSource() instanceof Button button)) {
            return;
        }
        Object data = button.getUserData();
        if (data == null) {
            return;
        }
        try {
            int value = Integer.parseInt(data.toString().trim());
            setReviewStarsValue(value);
        } catch (NumberFormatException ignored) {
        }
    }

    private void setupReviewStarsUi() {
        setReviewStarsValue(5);
    }

    private void setReviewStarsValue(int value) {
        selectedReviewStars = Math.max(1, Math.min(5, value));
        refreshReviewStarsVisual();
        if (lblReviewStarsValue != null) {
            lblReviewStarsValue.setText(selectedReviewStars + "/5");
        }
    }

    private void refreshReviewStarsVisual() {
        Button[] stars = {btnStar1, btnStar2, btnStar3, btnStar4, btnStar5};
        for (int i = 0; i < stars.length; i++) {
            Button star = stars[i];
            if (star == null) {
                continue;
            }
            star.getStyleClass().removeAll("star-btn-active", "star-btn-inactive");
            if (i < selectedReviewStars) {
                star.getStyleClass().add("star-btn-active");
            } else {
                star.getStyleClass().add("star-btn-inactive");
            }
        }
    }

    private void setReviewStarsDisabled(boolean disabled) {
        if (btnStar1 != null) btnStar1.setDisable(disabled);
        if (btnStar2 != null) btnStar2.setDisable(disabled);
        if (btnStar3 != null) btnStar3.setDisable(disabled);
        if (btnStar4 != null) btnStar4.setDisable(disabled);
        if (btnStar5 != null) btnStar5.setDisable(disabled);
    }
}
