package com.advisora.Services.ressource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.advisora.Model.ressource.ResourceMarketListing;
import com.advisora.Model.ressource.ResourceMarketOrder;
import com.advisora.Model.ressource.ResourceMarketReview;
import com.advisora.enums.ProjectStatus;
import com.advisora.utils.MyConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

public class ResourceMarketplaceService {
    private static final String STATUS_LISTED = "LISTED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_SOLD_OUT = "SOLD_OUT";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String DELIVERY_STATUS_PREPARING = "EN_PREPARATION";
    private static final String DELIVERY_STATUS_SENT = "ENVOYEE";
    private static final String OPENVERSE_IMAGE_API = "https://api.openverse.org/v1/images/";
    private static final String WIKIMEDIA_IMAGE_API = "https://commons.wikimedia.org/w/api.php";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile boolean schemaReady;
    private String qtyColumnCache;
    private final WalletService walletService = new WalletService();
    private final FiabiloDeliveryService fiabiloDeliveryService = new FiabiloDeliveryService();
    private final Map<String, String> openverseCache = new ConcurrentHashMap<>();

    public List<ResourceMarketListing> listActiveListingsForBuyer(int buyerId) {
        ensureSchema();
        String sql = "SELECT l.idListing, l.sellerUserId AS sellerId, l.idProj AS sourceProjectId, l.idRs, "
                + "l.qtyInitial AS qtyTotal, l.qtyRemaining AS qtyAvailable, l.unitPrice, l.note, l.status, l.imageUrl, l.createdAt, "
                + "COALESCE(rv.avgStars, 0) AS avgStars, COALESCE(rv.reviewCount, 0) AS reviewCount, "
                + "r.nomRs, cf.nomFr, p.titleProj, CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS sellerName "
                + "FROM resource_market_listing l "
                + "JOIN resources r ON r.idRs = l.idRs "
                + "JOIN cataloguefournisseur cf ON cf.idFr = r.idFr "
                + "JOIN projects p ON p.idProj = l.idProj "
                + "LEFT JOIN `user` u ON u.idUser = l.sellerUserId "
                + "LEFT JOIN (SELECT idListing, AVG(stars) AS avgStars, COUNT(*) AS reviewCount FROM resource_market_review GROUP BY idListing) rv "
                + "ON rv.idListing = l.idListing "
                + "WHERE l.status = ? AND l.qtyRemaining > 0 "
                + "ORDER BY l.createdAt DESC";
        return queryListings(sql, ps -> {
            ps.setString(1, STATUS_LISTED);
        });
    }

    public List<ResourceMarketListing> listMyListings(int sellerId) {
        ensureSchema();
        String sql = "SELECT l.idListing, l.sellerUserId AS sellerId, l.idProj AS sourceProjectId, l.idRs, "
                + "l.qtyInitial AS qtyTotal, l.qtyRemaining AS qtyAvailable, l.unitPrice, l.note, l.status, l.imageUrl, l.createdAt, "
                + "COALESCE(rv.avgStars, 0) AS avgStars, COALESCE(rv.reviewCount, 0) AS reviewCount, "
                + "r.nomRs, cf.nomFr, p.titleProj, CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS sellerName "
                + "FROM resource_market_listing l "
                + "JOIN resources r ON r.idRs = l.idRs "
                + "JOIN cataloguefournisseur cf ON cf.idFr = r.idFr "
                + "JOIN projects p ON p.idProj = l.idProj "
                + "LEFT JOIN `user` u ON u.idUser = l.sellerUserId "
                + "LEFT JOIN (SELECT idListing, AVG(stars) AS avgStars, COUNT(*) AS reviewCount FROM resource_market_review GROUP BY idListing) rv "
                + "ON rv.idListing = l.idListing "
                + "WHERE l.sellerUserId = ? "
                + "ORDER BY l.createdAt DESC";
        return queryListings(sql, ps -> ps.setInt(1, sellerId));
    }

    public List<ResourceMarketOrder> listBuyerOrders(int buyerId) {
        ensureSchema();
        String sql = "SELECT o.idOrder, o.idListing, o.buyerUserId AS buyerId, o.sellerUserId AS sellerId, "
                + "o.buyerProjectId AS targetProjectId, o.idRs, o.quantity AS qty, o.unitPrice, o.totalPrice AS totalAmount, "
                + "o.status, o.createdAt, p.titleProj, r.nomRs, "
                + "d.status AS deliveryStatus, d.trackingCode AS deliveryTrackingCode, d.labelUrl AS deliveryLabelUrl, "
                + "rv.stars AS reviewStars, rv.comment AS reviewComment, "
                + "CONCAT(COALESCE(us.PrenomUser,''), ' ', COALESCE(us.nomUser,'')) AS sellerName, "
                + "CONCAT(COALESCE(ub.PrenomUser,''), ' ', COALESCE(ub.nomUser,'')) AS buyerName "
                + "FROM resource_market_order o "
                + "LEFT JOIN projects p ON p.idProj = o.buyerProjectId "
                + "LEFT JOIN resources r ON r.idRs = o.idRs "
                + "LEFT JOIN resource_market_delivery d ON d.idOrder = o.idOrder "
                + "LEFT JOIN resource_market_review rv ON rv.idOrder = o.idOrder AND rv.reviewerUserId = o.buyerUserId "
                + "LEFT JOIN `user` us ON us.idUser = o.sellerUserId "
                + "LEFT JOIN `user` ub ON ub.idUser = o.buyerUserId "
                + "WHERE o.buyerUserId = ? "
                + "ORDER BY o.createdAt DESC";
        return queryOrders(sql, ps -> ps.setInt(1, buyerId));
    }

    public Map<Integer, List<ResourceMarketReview>> listRecentReviewsByListingIds(List<Integer> listingIds, int perListingLimit) {
        ensureSchema();
        Map<Integer, List<ResourceMarketReview>> out = new ConcurrentHashMap<>();
        if (listingIds == null || listingIds.isEmpty()) {
            return out;
        }

        List<Integer> validIds = listingIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (validIds.isEmpty()) {
            return out;
        }

        int maxPerListing = perListingLimit <= 0 ? 3 : Math.min(perListingLimit, 10);
        String placeholders = String.join(",", Collections.nCopies(validIds.size(), "?"));
        String sql = "SELECT rv.idReview, rv.idListing, rv.idOrder, rv.reviewerUserId, rv.stars, rv.comment, rv.createdAt, "
                + "CONCAT(COALESCE(u.PrenomUser,''), ' ', COALESCE(u.nomUser,'')) AS reviewerName "
                + "FROM resource_market_review rv "
                + "LEFT JOIN `user` u ON u.idUser = rv.reviewerUserId "
                + "WHERE rv.idListing IN (" + placeholders + ") "
                + "ORDER BY rv.idListing ASC, rv.createdAt DESC, rv.idReview DESC";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            int idx = 1;
            for (Integer id : validIds) {
                ps.setInt(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idListing = rs.getInt("idListing");
                    List<ResourceMarketReview> bucket = out.computeIfAbsent(idListing, k -> new ArrayList<>());
                    if (bucket.size() >= maxPerListing) {
                        continue;
                    }

                    ResourceMarketReview review = new ResourceMarketReview();
                    review.setIdReview(rs.getInt("idReview"));
                    review.setIdListing(idListing);
                    review.setIdOrder(rs.getInt("idOrder"));
                    review.setReviewerUserId(rs.getInt("reviewerUserId"));
                    review.setStars(rs.getInt("stars"));
                    review.setComment(rs.getString("comment"));
                    review.setCreatedAt(rs.getTimestamp("createdAt"));
                    String reviewerName = rs.getString("reviewerName");
                    review.setReviewerName(reviewerName == null || reviewerName.trim().isEmpty() ? "Client" : reviewerName.trim());
                    bucket.add(review);
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture avis marketplace: " + e.getMessage(), e);
        }
    }

    public void publishListingFromReservation(int sellerId, int sourceProjectId, int idRs, int quantity, double unitPrice, String note) {
        publishListingFromReservation(sellerId, sourceProjectId, idRs, quantity, unitPrice, note, null);
    }

    public void publishListingFromReservation(int sellerId, int sourceProjectId, int idRs, int quantity, double unitPrice, String note, String customImageUrl) {
        ensureSchema();
        if (sellerId <= 0) {
            throw new IllegalArgumentException("Client vendeur invalide.");
        }
        if (sourceProjectId <= 0 || idRs <= 0) {
            throw new IllegalArgumentException("Reservation source invalide.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantite > 0 obligatoire.");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("Prix unitaire invalide.");
        }

        String normalizedNote = normalizeNote(note);

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                assertProjectOwner(cnx, sourceProjectId, sellerId, "Projet source introuvable pour ce client.");
                int sellerQty = currentReservedForProjectResource(cnx, sourceProjectId, idRs);
                if (sellerQty <= 0) {
                    throw new IllegalArgumentException("Aucune quantite reservee a vendre pour cette ressource.");
                }

                int alreadyListed = currentActiveListedQty(cnx, sellerId, sourceProjectId, idRs);
                int listableQty = sellerQty - alreadyListed;
                if (listableQty <= 0) {
                    throw new IllegalArgumentException("Aucune quantite disponible pour une nouvelle annonce.");
                }
                if (quantity > listableQty) {
                    throw new IllegalArgumentException("Quantite maximale publiable: " + listableQty);
                }

                String imageUrl = normalizeImageUrl(customImageUrl);
                if (imageUrl == null) {
                    String resourceName = loadResourceName(cnx, idRs);
                    imageUrl = resolveMarketplaceImageUrl(resourceName, normalizedNote);
                }

                String sql = "INSERT INTO resource_market_listing (sellerUserId, idProj, idRs, qtyInitial, qtyRemaining, unitPrice, note, status, imageUrl) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setInt(1, sellerId);
                    ps.setInt(2, sourceProjectId);
                    ps.setInt(3, idRs);
                    ps.setInt(4, quantity);
                    ps.setInt(5, quantity);
                    ps.setDouble(6, unitPrice);
                    ps.setString(7, normalizedNote);
                    ps.setString(8, STATUS_LISTED);
                    ps.setString(9, imageUrl);
                    ps.executeUpdate();
                }

                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur publication annonce: " + e.getMessage(), e);
        }
    }

    public void cancelListing(int sellerId, int listingId) {
        ensureSchema();
        if (sellerId <= 0 || listingId <= 0) {
            throw new IllegalArgumentException("Annonce invalide.");
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                ListingLock listing = lockListing(cnx, listingId);
                if (listing == null) {
                    throw new IllegalArgumentException("Annonce introuvable.");
                }
                if (listing.sellerId != sellerId) {
                    throw new IllegalStateException("Acces refuse a cette annonce.");
                }
                if (!isActiveStatus(listing.status) || listing.qtyAvailable <= 0) {
                    throw new IllegalStateException("Cette annonce ne peut plus etre annulee.");
                }

                String sql = "UPDATE resource_market_listing SET status = ? WHERE idListing = ?";
                try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                    ps.setString(1, STATUS_CANCELLED);
                    ps.setInt(2, listingId);
                    ps.executeUpdate();
                }
                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur annulation annonce: " + e.getMessage(), e);
        }
    }

    public void buyListing(int buyerId, int listingId, int quantity, Integer targetProjectIdOrNull) {
        ensureSchema();
        if (buyerId <= 0 || listingId <= 0) {
            throw new IllegalArgumentException("Achat invalide.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantite > 0 obligatoire.");
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                ListingLock listing = lockListing(cnx, listingId);
                if (listing == null) {
                    throw new IllegalArgumentException("Annonce introuvable.");
                }
                if (!isActiveStatus(listing.status) || listing.qtyAvailable <= 0) {
                    throw new IllegalStateException("Annonce non disponible.");
                }
                if (listing.sellerId == buyerId) {
                    throw new IllegalStateException("Vous ne pouvez pas acheter votre propre annonce.");
                }
                if (quantity > listing.qtyAvailable) {
                    throw new IllegalArgumentException("Quantite disponible sur annonce: " + listing.qtyAvailable);
                }

                assertProjectOwner(cnx, listing.sourceProjectId, listing.sellerId, "Projet vendeur introuvable.");
                int sellerCurrentQty = currentReservedForProjectResource(cnx, listing.sourceProjectId, listing.idRs);
                if (sellerCurrentQty < quantity) {
                    throw new IllegalStateException("Le vendeur ne dispose plus du stock reserve necessaire.");
                }

                int buyerProjectId = resolveOrCreateProject(cnx, buyerId, targetProjectIdOrNull);
                double totalAmount = listing.unitPrice * quantity;

                walletService.debitForShopPurchase(cnx, buyerId, totalAmount,
                        "BUY listing#" + listing.idListing + " rs#" + listing.idRs);
                walletService.creditForShopSale(cnx, listing.sellerId, totalAmount,
                        "SELL listing#" + listing.idListing + " buyer#" + buyerId);

                replaceReservationQuantity(cnx, listing.sourceProjectId, listing.idRs, sellerCurrentQty - quantity);
                increaseReservationQuantity(cnx, buyerProjectId, listing.idRs, quantity);

                int remaining = listing.qtyAvailable - quantity;
                String nextStatus = remaining == 0 ? STATUS_SOLD_OUT : STATUS_LISTED;
                try (PreparedStatement ps = cnx.prepareStatement(
                        "UPDATE resource_market_listing SET qtyRemaining = ?, status = ? WHERE idListing = ?")) {
                    ps.setInt(1, remaining);
                    ps.setString(2, nextStatus);
                    ps.setInt(3, listing.idListing);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = cnx.prepareStatement(
                        "INSERT INTO resource_market_order (idListing, buyerUserId, sellerUserId, buyerProjectId, idRs, quantity, unitPrice, totalPrice, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, listing.idListing);
                    ps.setInt(2, buyerId);
                    ps.setInt(3, listing.sellerId);
                    ps.setInt(4, buyerProjectId);
                    ps.setInt(5, listing.idRs);
                    ps.setInt(6, quantity);
                    ps.setDouble(7, listing.unitPrice);
                    ps.setDouble(8, totalAmount);
                    ps.setString(9, STATUS_CONFIRMED);
                    ps.executeUpdate();
                }

                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur achat annonce: " + e.getMessage(), e);
        }
    }

    public CheckoutResult checkoutAndBuyListing(
            int buyerId,
            int listingId,
            int quantity,
            Integer targetProjectIdOrNull,
            CheckoutRequest checkout
    ) {
        ensureSchema();
        if (buyerId <= 0 || listingId <= 0) {
            throw new IllegalArgumentException("Achat invalide.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantite > 0 obligatoire.");
        }
        validateCheckoutRequest(checkout);

        int orderId;
        int deliveryId;
        int idRs;
        double totalAmount;
        String resourceName;

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                ListingLock listing = lockListing(cnx, listingId);
                if (listing == null) {
                    throw new IllegalArgumentException("Annonce introuvable.");
                }
                if (!isActiveStatus(listing.status) || listing.qtyAvailable <= 0) {
                    throw new IllegalStateException("Annonce non disponible.");
                }
                if (listing.sellerId == buyerId) {
                    throw new IllegalStateException("Vous ne pouvez pas acheter votre propre annonce.");
                }
                if (quantity > listing.qtyAvailable) {
                    throw new IllegalArgumentException("Quantite disponible sur annonce: " + listing.qtyAvailable);
                }

                assertProjectOwner(cnx, listing.sourceProjectId, listing.sellerId, "Projet vendeur introuvable.");
                int sellerCurrentQty = currentReservedForProjectResource(cnx, listing.sourceProjectId, listing.idRs);
                if (sellerCurrentQty < quantity) {
                    throw new IllegalStateException("Le vendeur ne dispose plus du stock reserve necessaire.");
                }

                int buyerProjectId = resolveOrCreateProject(cnx, buyerId, targetProjectIdOrNull);
                totalAmount = listing.unitPrice * quantity;

                walletService.debitForShopPurchase(cnx, buyerId, totalAmount,
                        "BUY listing#" + listing.idListing + " rs#" + listing.idRs);
                walletService.creditForShopSale(cnx, listing.sellerId, totalAmount,
                        "SELL listing#" + listing.idListing + " buyer#" + buyerId);

                replaceReservationQuantity(cnx, listing.sourceProjectId, listing.idRs, sellerCurrentQty - quantity);
                increaseReservationQuantity(cnx, buyerProjectId, listing.idRs, quantity);

                int remaining = listing.qtyAvailable - quantity;
                String nextStatus = remaining == 0 ? STATUS_SOLD_OUT : STATUS_LISTED;
                try (PreparedStatement ps = cnx.prepareStatement(
                        "UPDATE resource_market_listing SET qtyRemaining = ?, status = ? WHERE idListing = ?")) {
                    ps.setInt(1, remaining);
                    ps.setString(2, nextStatus);
                    ps.setInt(3, listing.idListing);
                    ps.executeUpdate();
                }

                orderId = insertMarketOrder(cnx, listing, buyerId, buyerProjectId, quantity, totalAmount);
                idRs = listing.idRs;
                resourceName = loadResourceName(cnx, listing.idRs);
                deliveryId = insertDeliveryDraft(cnx, orderId, buyerId, checkout, resourceName, quantity, totalAmount);

                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur checkout annonce: " + e.getMessage(), e);
        }

        FiabiloDeliveryService.ShipmentRequest shipment = new FiabiloDeliveryService.ShipmentRequest();
        shipment.setRecipientFullName((checkout.getFirstName() + " " + checkout.getLastName()).trim());
        shipment.setCity(checkout.getCity());
        shipment.setAddressLine(checkout.getAddressLine());
        shipment.setPhone(checkout.getPhone());
        shipment.setPhone2(checkout.getPhone2());
        shipment.setDesignation(resourceName == null ? ("Resource #" + idRs) : resourceName);
        shipment.setQuantity(quantity);
        shipment.setTotalPrice(totalAmount);
        shipment.setMessage("Commande Mini Shop #" + orderId);

        FiabiloDeliveryService.DispatchResult dispatch = fiabiloDeliveryService.createShipment(shipment);
        String deliveryStatus = dispatch.isSent() ? DELIVERY_STATUS_SENT : DELIVERY_STATUS_PREPARING;
        updateDeliveryAfterDispatch(deliveryId, deliveryStatus, dispatch);

        CheckoutResult result = new CheckoutResult();
        result.setOrderId(orderId);
        result.setDeliveryStatus(deliveryStatus);
        result.setTrackingCode(dispatch.getTrackingCode());
        result.setLabelUrl(dispatch.getLabelUrl());
        result.setDeliveryMessage(dispatch.getMessage());
        return result;
    }

    public void addOrUpdateReviewForOrder(int buyerId, int orderId, int stars, String comment) {
        ensureSchema();
        if (buyerId <= 0 || orderId <= 0) {
            throw new IllegalArgumentException("Avis invalide.");
        }
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("La note doit etre entre 1 et 5.");
        }

        String normalizedComment = normalizeReviewComment(comment);

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                OrderReviewLock order = lockOrderForReview(cnx, orderId);
                if (order == null) {
                    throw new IllegalArgumentException("Commande introuvable.");
                }
                if (order.buyerId != buyerId) {
                    throw new IllegalStateException("Acces refuse a cette commande.");
                }
                if (!STATUS_CONFIRMED.equalsIgnoreCase(order.status)) {
                    throw new IllegalStateException("Avis disponible uniquement pour les commandes CONFIRMED.");
                }

                Integer existingReviewId = findReviewIdByOrderAndBuyer(cnx, orderId, buyerId);
                if (existingReviewId == null) {
                    String insertSql = "INSERT INTO resource_market_review (idListing, idOrder, reviewerUserId, stars, comment) "
                            + "VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = cnx.prepareStatement(insertSql)) {
                        ps.setInt(1, order.idListing);
                        ps.setInt(2, order.idOrder);
                        ps.setInt(3, buyerId);
                        ps.setInt(4, stars);
                        ps.setString(5, normalizedComment);
                        ps.executeUpdate();
                    }
                } else {
                    String updateSql = "UPDATE resource_market_review SET stars = ?, comment = ? WHERE idReview = ?";
                    try (PreparedStatement ps = cnx.prepareStatement(updateSql)) {
                        ps.setInt(1, stars);
                        ps.setString(2, normalizedComment);
                        ps.setInt(3, existingReviewId);
                        ps.executeUpdate();
                    }
                }

                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur enregistrement avis: " + e.getMessage(), e);
        }
    }

    private List<ResourceMarketListing> queryListings(String sql, PreparedStatementConfigurer configurer) {
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            if (configurer != null) {
                configurer.configure(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ResourceMarketListing> out = new ArrayList<>();
                while (rs.next()) {
                    ResourceMarketListing listing = new ResourceMarketListing();
                    listing.setIdListing(rs.getInt("idListing"));
                    listing.setSellerId(rs.getInt("sellerId"));
                    listing.setSellerName(rs.getString("sellerName"));
                    listing.setSourceProjectId(rs.getInt("sourceProjectId"));
                    listing.setSourceProjectTitle(rs.getString("titleProj"));
                    listing.setIdRs(rs.getInt("idRs"));
                    listing.setResourceName(rs.getString("nomRs"));
                    listing.setFournisseurName(rs.getString("nomFr"));
                    listing.setQtyTotal(rs.getInt("qtyTotal"));
                    listing.setQtyAvailable(rs.getInt("qtyAvailable"));
                    listing.setUnitPrice(rs.getDouble("unitPrice"));
                    listing.setNote(rs.getString("note"));
                    listing.setStatus(rs.getString("status"));
                    listing.setImageUrl(rs.getString("imageUrl"));
                    listing.setAverageStars(rs.getDouble("avgStars"));
                    listing.setReviewCount(rs.getInt("reviewCount"));
                    listing.setCreatedAt(rs.getTimestamp("createdAt"));
                    out.add(listing);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture annonces marketplace: " + e.getMessage(), e);
        }
    }

    private List<ResourceMarketOrder> queryOrders(String sql, PreparedStatementConfigurer configurer) {
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            if (configurer != null) {
                configurer.configure(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ResourceMarketOrder> out = new ArrayList<>();
                while (rs.next()) {
                    ResourceMarketOrder order = new ResourceMarketOrder();
                    order.setIdOrder(rs.getInt("idOrder"));
                    order.setIdListing(rs.getInt("idListing"));
                    order.setBuyerId(rs.getInt("buyerId"));
                    order.setBuyerName(rs.getString("buyerName"));
                    order.setSellerId(rs.getInt("sellerId"));
                    order.setSellerName(rs.getString("sellerName"));
                    order.setIdRs(rs.getInt("idRs"));
                    order.setResourceName(rs.getString("nomRs"));
                    order.setQty(rs.getInt("qty"));
                    order.setUnitPrice(rs.getDouble("unitPrice"));
                    order.setTotalAmount(rs.getDouble("totalAmount"));
                    order.setStatus(rs.getString("status"));
                    order.setDeliveryStatus(rs.getString("deliveryStatus"));
                    order.setDeliveryTrackingCode(rs.getString("deliveryTrackingCode"));
                    order.setDeliveryLabelUrl(rs.getString("deliveryLabelUrl"));
                    order.setReviewStars(rs.getInt("reviewStars"));
                    order.setReviewComment(rs.getString("reviewComment"));
                    order.setTargetProjectId(rs.getInt("targetProjectId"));
                    order.setTargetProjectTitle(rs.getString("titleProj"));
                    order.setCreatedAt(rs.getTimestamp("createdAt"));
                    out.add(order);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture achats marketplace: " + e.getMessage(), e);
        }
    }

    private int insertMarketOrder(Connection cnx, ListingLock listing, int buyerId, int buyerProjectId, int quantity, double totalAmount) throws SQLException {
        try (PreparedStatement ps = cnx.prepareStatement(
                "INSERT INTO resource_market_order (idListing, buyerUserId, sellerUserId, buyerProjectId, idRs, quantity, unitPrice, totalPrice, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, listing.idListing);
            ps.setInt(2, buyerId);
            ps.setInt(3, listing.sellerId);
            ps.setInt(4, buyerProjectId);
            ps.setInt(5, listing.idRs);
            ps.setInt(6, quantity);
            ps.setDouble(7, listing.unitPrice);
            ps.setDouble(8, totalAmount);
            ps.setString(9, STATUS_CONFIRMED);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Creation commande marketplace echouee.");
    }

    private int insertDeliveryDraft(Connection cnx, int orderId, int buyerId, CheckoutRequest checkout,
                                    String resourceName, int quantity, double totalAmount) throws SQLException {
        String sql = "INSERT INTO resource_market_delivery "
                + "(idOrder, buyerUserId, recipientName, city, addressLine, phone, phone2, "
                + "resourceName, quantity, totalPrice, status, provider, providerMessage) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderId);
            ps.setInt(2, buyerId);
            ps.setString(3, (checkout.getFirstName() + " " + checkout.getLastName()).trim());
            ps.setString(4, checkout.getCity());
            ps.setString(5, checkout.getAddressLine());
            ps.setString(6, checkout.getPhone());
            ps.setString(7, checkout.getPhone2());
            ps.setString(8, resourceName);
            ps.setInt(9, quantity);
            ps.setDouble(10, totalAmount);
            ps.setString(11, DELIVERY_STATUS_PREPARING);
            ps.setString(12, "FIABILO");
            ps.setString(13, "Commande creee, en attente d'envoi transporteur.");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Creation livraison marketplace echouee.");
    }

    private void updateDeliveryAfterDispatch(int deliveryId, String status, FiabiloDeliveryService.DispatchResult dispatch) {
        String sql = "UPDATE resource_market_delivery "
                + "SET status = ?, trackingCode = ?, labelUrl = ?, providerMessage = ?, updatedAt = NOW() "
                + "WHERE idDelivery = ?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, dispatch == null ? null : dispatch.getTrackingCode());
            ps.setString(3, dispatch == null ? null : dispatch.getLabelUrl());
            ps.setString(4, dispatch == null ? "Envoi livraison non tente." : dispatch.getMessage());
            ps.setInt(5, deliveryId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void validateCheckoutRequest(CheckoutRequest checkout) {
        if (checkout == null) {
            throw new IllegalArgumentException("Informations de livraison manquantes.");
        }
        if (isBlank(checkout.getFirstName())) {
            throw new IllegalArgumentException("Nom obligatoire.");
        }
        if (isBlank(checkout.getLastName())) {
            throw new IllegalArgumentException("Prenom obligatoire.");
        }
        if (isBlank(checkout.getCity())) {
            throw new IllegalArgumentException("Ville obligatoire.");
        }
        if (isBlank(checkout.getAddressLine())) {
            throw new IllegalArgumentException("Adresse complete obligatoire.");
        }
        if (!isBlank(checkout.getPhone()) && checkout.getPhone().trim().length() < 8) {
            throw new IllegalArgumentException("Telephone invalide.");
        }
    }

    private int resolveOrCreateProject(Connection cnx, int clientId, Integer projectIdOrNull) throws SQLException {
        if (projectIdOrNull != null && projectIdOrNull > 0) {
            assertProjectOwner(cnx, projectIdOrNull, clientId, "Projet cible introuvable pour ce client.");
            return projectIdOrNull;
        }

        String findSql = "SELECT idProj FROM projects WHERE idClient = ? ORDER BY idProj DESC LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(findSql)) {
            ps.setInt(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        String insertSql = "INSERT INTO projects (titleProj, descriptionProj, budgetProj, typeProj, stateProj, createdAtProj, updatedAtProj, avancementProj, idClient) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Marketplace Purchase");
            ps.setString(2, "Cree automatiquement pour achat marketplace");
            ps.setDouble(3, 0.0);
            ps.setString(4, "RESOURCE");
            ps.setString(5, ProjectStatus.PENDING.name());
            ps.setDouble(6, 0.0);
            ps.setInt(7, clientId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Impossible de creer un projet cible.");
    }

    private void assertProjectOwner(Connection cnx, int projectId, int ownerClientId, String message) throws SQLException {
        String sql = "SELECT idProj FROM projects WHERE idProj = ? AND idClient = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setInt(2, ownerClientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(message);
                }
            }
        }
    }

    private int currentReservedForProjectResource(Connection cnx, int idProj, int idRs) throws SQLException {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        String sql = qtyColumn == null
                ? "SELECT COUNT(*) FROM project_resources WHERE idProj = ? AND idRs = ?"
                : "SELECT COALESCE(SUM(" + qtyColumn + "), 0) FROM project_resources WHERE idProj = ? AND idRs = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            ps.setInt(2, idRs);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int currentActiveListedQty(Connection cnx, int sellerId, int sourceProjectId, int idRs) throws SQLException {
        String sql = "SELECT COALESCE(SUM(qtyRemaining), 0) FROM resource_market_listing "
                + "WHERE sellerUserId = ? AND idProj = ? AND idRs = ? AND status = ? AND qtyRemaining > 0";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            ps.setInt(2, sourceProjectId);
            ps.setInt(3, idRs);
            ps.setString(4, STATUS_LISTED);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void replaceReservationQuantity(Connection cnx, int idProj, int idRs, int newQuantity) throws SQLException {
        String qtyColumn = detectProjectResourcesQuantityColumn();

        try (PreparedStatement deletePs = cnx.prepareStatement("DELETE FROM project_resources WHERE idProj = ? AND idRs = ?")) {
            deletePs.setInt(1, idProj);
            deletePs.setInt(2, idRs);
            deletePs.executeUpdate();
        }

        if (newQuantity <= 0) {
            return;
        }

        if (qtyColumn == null) {
            try (PreparedStatement ps = cnx.prepareStatement("INSERT INTO project_resources (idProj, idRs) VALUES (?, ?)")) {
                for (int i = 0; i < newQuantity; i++) {
                    ps.setInt(1, idProj);
                    ps.setInt(2, idRs);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return;
        }

        String sql = "INSERT INTO project_resources (idProj, idRs, " + qtyColumn + ") VALUES (?, ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            ps.setInt(2, idRs);
            ps.setInt(3, newQuantity);
            ps.executeUpdate();
        }
    }

    private void increaseReservationQuantity(Connection cnx, int idProj, int idRs, int quantity) throws SQLException {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        if (qtyColumn == null) {
            try (PreparedStatement ps = cnx.prepareStatement("INSERT INTO project_resources (idProj, idRs) VALUES (?, ?)")) {
                for (int i = 0; i < quantity; i++) {
                    ps.setInt(1, idProj);
                    ps.setInt(2, idRs);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return;
        }

        String sql = "INSERT INTO project_resources (idProj, idRs, " + qtyColumn + ") VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE " + qtyColumn + " = " + qtyColumn + " + VALUES(" + qtyColumn + ")";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idProj);
            ps.setInt(2, idRs);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    private ListingLock lockListing(Connection cnx, int listingId) throws SQLException {
        String sql = "SELECT idListing, sellerUserId, idProj, idRs, qtyRemaining, unitPrice, status "
                + "FROM resource_market_listing WHERE idListing = ? FOR UPDATE";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ListingLock lock = new ListingLock();
                lock.idListing = rs.getInt("idListing");
                lock.sellerId = rs.getInt("sellerUserId");
                lock.sourceProjectId = rs.getInt("idProj");
                lock.idRs = rs.getInt("idRs");
                lock.qtyAvailable = rs.getInt("qtyRemaining");
                lock.unitPrice = rs.getDouble("unitPrice");
                lock.status = rs.getString("status");
                return lock;
            }
        }
    }

    private String detectProjectResourcesQuantityColumn() {
        if (qtyColumnCache != null) {
            return "__NONE__".equals(qtyColumnCache) ? null : qtyColumnCache;
        }

        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='project_resources'";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String col = rs.getString(1);
                if ("quantite".equalsIgnoreCase(col)
                        || "quantity".equalsIgnoreCase(col)
                        || "qty".equalsIgnoreCase(col)
                        || "qtyAllocated".equalsIgnoreCase(col)) {
                    qtyColumnCache = col;
                    return qtyColumnCache;
                }
            }
            qtyColumnCache = "__NONE__";
            return null;
        } catch (SQLException e) {
            qtyColumnCache = "__NONE__";
            return null;
        }
    }

    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            try (Connection cnx = MyConnection.getInstance().getConnection();
                 Statement st = cnx.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS resource_market_listing (
                            idListing INT AUTO_INCREMENT PRIMARY KEY,
                            sellerUserId INT NOT NULL,
                            idProj INT NOT NULL,
                            idRs INT NOT NULL,
                            qtyInitial INT NOT NULL,
                            qtyRemaining INT NOT NULL,
                            unitPrice DECIMAL(12,3) NOT NULL,
                            note VARCHAR(300) NULL,
                            imageUrl VARCHAR(600) NULL,
                            status VARCHAR(20) NOT NULL DEFAULT 'LISTED',
                            createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            INDEX idx_rml_seller_user (sellerUserId),
                            INDEX idx_rml_rs (idRs),
                            INDEX idx_rml_status (status)
                        )
                        """);
                st.execute("""
                        CREATE TABLE IF NOT EXISTS resource_market_order (
                            idOrder INT AUTO_INCREMENT PRIMARY KEY,
                            idListing INT NOT NULL,
                            buyerUserId INT NOT NULL,
                            sellerUserId INT NOT NULL,
                            buyerProjectId INT NOT NULL,
                            idRs INT NOT NULL,
                            quantity INT NOT NULL,
                            unitPrice DECIMAL(12,3) NOT NULL,
                            totalPrice DECIMAL(12,3) NOT NULL,
                            status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
                            createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            INDEX idx_rmo_listing (idListing),
                            INDEX idx_rmo_buyer (buyerUserId),
                            INDEX idx_rmo_seller (sellerUserId)
                        )
                        """);
                st.execute("""
                        CREATE TABLE IF NOT EXISTS resource_market_review (
                            idReview INT AUTO_INCREMENT PRIMARY KEY,
                            idListing INT NOT NULL,
                            stars INT NOT NULL,
                            comment VARCHAR(400) NULL,
                            createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            INDEX idx_rmr_listing (idListing)
                        )
                        """);
                st.execute("""
                        CREATE TABLE IF NOT EXISTS resource_market_delivery (
                            idDelivery INT AUTO_INCREMENT PRIMARY KEY,
                            idOrder INT NOT NULL,
                            buyerUserId INT NOT NULL,
                            recipientName VARCHAR(160) NOT NULL,
                            city VARCHAR(120) NOT NULL,
                            addressLine VARCHAR(260) NOT NULL,
                            phone VARCHAR(40) NULL,
                            phone2 VARCHAR(40) NULL,
                            resourceName VARCHAR(200) NULL,
                            quantity INT NOT NULL DEFAULT 1,
                            totalPrice DECIMAL(12,3) NOT NULL DEFAULT 0,
                            status VARCHAR(30) NOT NULL DEFAULT 'EN_PREPARATION',
                            provider VARCHAR(40) NOT NULL DEFAULT 'FIABILO',
                            trackingCode VARCHAR(140) NULL,
                            labelUrl VARCHAR(700) NULL,
                            providerMessage VARCHAR(400) NULL,
                            createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            INDEX idx_rmd_order (idOrder),
                            INDEX idx_rmd_buyer (buyerUserId),
                            INDEX idx_rmd_status (status)
                        )
                        """);

                migrateLegacyColumns(cnx);
                schemaReady = true;
            } catch (SQLException e) {
                throw new RuntimeException("Erreur initialisation tables marketplace: " + e.getMessage(), e);
            }
        }
    }

    private void migrateLegacyColumns(Connection cnx) throws SQLException {
        ensureColumn(cnx, "resource_market_listing", "sellerUserId", "INT NULL");
        ensureColumn(cnx, "resource_market_listing", "idProj", "INT NULL");
        ensureColumn(cnx, "resource_market_listing", "qtyInitial", "INT NULL");
        ensureColumn(cnx, "resource_market_listing", "qtyRemaining", "INT NULL");
        ensureColumn(cnx, "resource_market_listing", "imageUrl", "VARCHAR(600) NULL");

        copyColumnIfExists(cnx, "resource_market_listing", "sellerUserId", "sellerId");
        copyColumnIfExists(cnx, "resource_market_listing", "idProj", "sourceProjectId");
        copyColumnIfExists(cnx, "resource_market_listing", "qtyInitial", "qtyTotal");
        copyColumnIfExists(cnx, "resource_market_listing", "qtyRemaining", "qtyAvailable");
        copyColumnIfExists(cnx, "resource_market_listing", "imageUrl", "thumbnailUrl");
        normalizeLegacyListingStatuses(cnx);

        ensureColumn(cnx, "resource_market_order", "buyerUserId", "INT NULL");
        ensureColumn(cnx, "resource_market_order", "sellerUserId", "INT NULL");
        ensureColumn(cnx, "resource_market_order", "buyerProjectId", "INT NULL");
        ensureColumn(cnx, "resource_market_order", "idRs", "INT NULL");
        ensureColumn(cnx, "resource_market_order", "quantity", "INT NULL");
        ensureColumn(cnx, "resource_market_order", "totalPrice", "DECIMAL(12,3) NULL");

        copyColumnIfExists(cnx, "resource_market_order", "buyerUserId", "buyerId");
        copyColumnIfExists(cnx, "resource_market_order", "sellerUserId", "sellerId");
        copyColumnIfExists(cnx, "resource_market_order", "buyerProjectId", "targetProjectId");
        copyColumnIfExists(cnx, "resource_market_order", "idRs", "resourceId");
        copyColumnIfExists(cnx, "resource_market_order", "quantity", "qty");
        copyColumnIfExists(cnx, "resource_market_order", "totalPrice", "totalAmount");

        ensureColumn(cnx, "resource_market_review", "idOrder", "INT NULL");
        ensureColumn(cnx, "resource_market_review", "reviewerUserId", "INT NULL");

        ensureColumn(cnx, "resource_market_delivery", "idOrder", "INT NULL");
        ensureColumn(cnx, "resource_market_delivery", "buyerUserId", "INT NULL");
        ensureColumn(cnx, "resource_market_delivery", "recipientName", "VARCHAR(160) NULL");
        ensureColumn(cnx, "resource_market_delivery", "city", "VARCHAR(120) NULL");
        ensureColumn(cnx, "resource_market_delivery", "addressLine", "VARCHAR(260) NULL");
        ensureColumn(cnx, "resource_market_delivery", "phone", "VARCHAR(40) NULL");
        ensureColumn(cnx, "resource_market_delivery", "phone2", "VARCHAR(40) NULL");
        ensureColumn(cnx, "resource_market_delivery", "resourceName", "VARCHAR(200) NULL");
        ensureColumn(cnx, "resource_market_delivery", "quantity", "INT NULL");
        ensureColumn(cnx, "resource_market_delivery", "totalPrice", "DECIMAL(12,3) NULL");
        ensureColumn(cnx, "resource_market_delivery", "status", "VARCHAR(30) NULL");
        ensureColumn(cnx, "resource_market_delivery", "provider", "VARCHAR(40) NULL");
        ensureColumn(cnx, "resource_market_delivery", "trackingCode", "VARCHAR(140) NULL");
        ensureColumn(cnx, "resource_market_delivery", "labelUrl", "VARCHAR(700) NULL");
        ensureColumn(cnx, "resource_market_delivery", "providerMessage", "VARCHAR(400) NULL");
    }

    private void ensureColumn(Connection cnx, String table, String column, String definition) throws SQLException {
        if (hasColumn(cnx, table, column)) {
            return;
        }
        try (Statement st = cnx.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void copyColumnIfExists(Connection cnx, String table, String targetColumn, String sourceColumn) throws SQLException {
        if (!hasColumn(cnx, table, targetColumn) || !hasColumn(cnx, table, sourceColumn)) {
            return;
        }
        String sql = "UPDATE " + table + " SET " + targetColumn + " = " + sourceColumn
                + " WHERE " + targetColumn + " IS NULL AND " + sourceColumn + " IS NOT NULL";
        try (Statement st = cnx.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private boolean hasColumn(Connection cnx, String table, String column) throws SQLException {
        String sql = "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void normalizeLegacyListingStatuses(Connection cnx) throws SQLException {
        if (!hasColumn(cnx, "resource_market_listing", "status")) {
            return;
        }
        String sql = "UPDATE resource_market_listing "
                + "SET status = ? "
                + "WHERE UPPER(COALESCE(status, '')) IN (?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, STATUS_LISTED);
            ps.setString(2, STATUS_ACTIVE);
            ps.setString(3, STATUS_OPEN);
            ps.executeUpdate();
        }
    }

    private boolean isActiveStatus(String status) {
        if (status == null) {
            return false;
        }
        return STATUS_LISTED.equalsIgnoreCase(status)
                || STATUS_ACTIVE.equalsIgnoreCase(status)
                || STATUS_OPEN.equalsIgnoreCase(status);
    }

    private Integer findReviewIdByOrderAndBuyer(Connection cnx, int orderId, int buyerId) throws SQLException {
        String sql = "SELECT idReview FROM resource_market_review "
                + "WHERE idOrder = ? AND reviewerUserId = ? "
                + "ORDER BY idReview DESC LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return null;
            }
        }
    }

    private OrderReviewLock lockOrderForReview(Connection cnx, int orderId) throws SQLException {
        String sql = "SELECT idOrder, idListing, buyerUserId, status FROM resource_market_order WHERE idOrder = ? FOR UPDATE";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                OrderReviewLock lock = new OrderReviewLock();
                lock.idOrder = rs.getInt("idOrder");
                lock.idListing = rs.getInt("idListing");
                lock.buyerId = rs.getInt("buyerUserId");
                lock.status = rs.getString("status");
                return lock;
            }
        }
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 300) {
            return normalized.substring(0, 300);
        }
        return normalized;
    }

    private String normalizeReviewComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 400) {
            return normalized.substring(0, 400);
        }
        return normalized;
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        String normalized = imageUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 600) {
            return normalized.substring(0, 600);
        }
        return normalized;
    }

    private String loadResourceName(Connection cnx, int idRs) {
        String sql = "SELECT nomRs FROM resources WHERE idRs = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idRs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException ignored) {
            return "resource";
        }
        return "resource";
    }

    private String buildMarketplaceImageUrl(String resourceName, String note) {
        String tags = buildLoremFlickrTags(resourceName, note);
        if (tags.isBlank()) {
            tags = "product,item,ecommerce";
        }
        return "https://loremflickr.com/640/360/" + tags + "?lock=" + Math.abs(tags.hashCode());
    }

    public String resolveMarketplaceImageForDisplay(String currentImageUrl, String resourceName, String note) {
        String direct = normalizeImageUrl(currentImageUrl);
        if (!isBlank(direct) && isLocalUploadedImage(direct)) {
            return direct;
        }
        return resolveMarketplaceImageUrl(resourceName, note);
    }

    private String resolveMarketplaceImageUrl(String resourceName, String note) {
        List<String> queries = buildOpenverseQueryCandidates(resourceName, note);
        for (String query : queries) {
            String cacheKey = query.toLowerCase(Locale.ROOT);
            String cached = openverseCache.get(cacheKey);
            if (!isBlank(cached)) {
                return cached;
            }

            String apiImage = fetchOpenverseImage(query);
            if (isBlank(apiImage)) {
                apiImage = fetchWikimediaImage(query);
            }
            if (!isBlank(apiImage)) {
                openverseCache.put(cacheKey, apiImage);
                return apiImage;
            }
        }
        return buildMarketplaceImageUrl(resourceName, note);
    }

    private List<String> buildOpenverseQueryCandidates(String resourceName, String note) {
        String name = normalizeQueryPart(resourceName);
        String description = normalizeQueryPart(note);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        LinkedHashSet<String> aliases = inferSemanticImageAliases(name + " " + description);

        for (String alias : aliases) {
            out.add(alias + " product photo");
            out.add(alias + " isolated product");
            out.add(alias + " ecommerce item");
        }

        if (!name.isBlank()) {
            out.add(name + " product");
            out.add(name);
        }
        if (!description.isBlank()) {
            out.add(truncateWords(description, 6) + " product");
        }
        if (!name.isBlank() && !description.isBlank()) {
            out.add(name + " " + truncateWords(description, 4));
        }
        out.add("product item");

        List<String> queries = new ArrayList<>();
        for (String q : out) {
            String cleaned = normalizeQueryPart(q);
            if (!cleaned.isBlank()) {
                queries.add(cleaned);
            }
        }
        return queries;
    }

    private String buildLoremFlickrTags(String resourceName, String note) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String base = normalizeQueryPart(resourceName);
        String extra = normalizeQueryPart(note);
        LinkedHashSet<String> aliases = inferSemanticImageAliases(base + " " + extra);

        for (String alias : aliases) {
            for (String token : alias.split("\\s+")) {
                String t = token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
                if (t.length() >= 3) {
                    tags.add(t);
                }
                if (tags.size() >= 5) {
                    break;
                }
            }
            if (tags.size() >= 5) {
                break;
            }
        }

        for (String token : (base + " " + truncateWords(extra, 3)).trim().split("\\s+")) {
            String t = token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
            if (t.length() >= 3) {
                tags.add(t);
            }
            if (tags.size() >= 5) {
                break;
            }
        }

        tags.add("product");
        tags.add("item");
        return String.join(",", tags);
    }

    private LinkedHashSet<String> inferSemanticImageAliases(String text) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String lower = safeLower(normalizeQueryPart(text));

        if (containsAny(lower, "montre", "watch", "swatch", "horloge")) {
            aliases.add("watch");
            aliases.add("wristwatch");
        }
        if (containsAny(lower, "bois", "wood", "timber", "lumber")) {
            aliases.add("wood material");
            aliases.add("timber plank");
        }
        if (containsAny(lower, "ordinateur", "laptop", "computer", "pc")) {
            aliases.add("laptop");
            aliases.add("computer device");
        }
        if (containsAny(lower, "projecteur", "projector", "beamer")) {
            aliases.add("projector");
        }
        if (containsAny(lower, "imprimante", "printer")) {
            aliases.add("printer");
        }
        if (containsAny(lower, "telephone", "phone", "smartphone", "mobile")) {
            aliases.add("smartphone");
        }
        if (containsAny(lower, "table", "desk")) {
            aliases.add("desk table");
        }
        if (containsAny(lower, "chaise", "chair", "seat")) {
            aliases.add("chair furniture");
        }
        if (containsAny(lower, "camera", "cam", "photo")) {
            aliases.add("camera device");
        }
        if (containsAny(lower, "casque", "headset", "headphone")) {
            aliases.add("headphones");
        }
        return aliases;
    }

    private boolean containsAny(String text, String... keywords) {
        if (isBlank(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncateWords(String text, int maxWords) {
        if (isBlank(text) || maxWords <= 0) {
            return "";
        }
        String[] tokens = text.trim().split("\\s+");
        int size = Math.min(tokens.length, maxWords);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens[i]);
        }
        return sb.toString().trim();
    }

    private String fetchOpenverseImage(String query) {
        try {
            String endpoint = OPENVERSE_IMAGE_API
                    + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&page_size=20&mature=false";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Advisora-MiniShop/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonNode root = JSON.readTree(response.body());
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return null;
            }

            int bestScore = Integer.MIN_VALUE;
            String bestUrl = null;
            for (JsonNode item : results) {
                String candidate = pickImageUrl(item);
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                int score = scoreCandidate(item, candidate, query);
                if (score > bestScore) {
                    bestScore = score;
                    bestUrl = candidate;
                }
            }
            return bestUrl;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchWikimediaImage(String query) {
        try {
            String endpoint = WIKIMEDIA_IMAGE_API
                    + "?action=query"
                    + "&generator=search"
                    + "&gsrnamespace=6"
                    + "&gsrlimit=20"
                    + "&prop=imageinfo"
                    + "&iiprop=url"
                    + "&iiurlwidth=640"
                    + "&format=json"
                    + "&origin=*"
                    + "&gsrsearch=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Advisora-MiniShop/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            JsonNode pages = JSON.readTree(response.body()).path("query").path("pages");
            if (!pages.isObject()) {
                return null;
            }

            int bestScore = Integer.MIN_VALUE;
            String bestUrl = null;
            var fields = pages.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> page = fields.next();
                JsonNode node = page.getValue();
                JsonNode imageInfo = node.path("imageinfo");
                if (!imageInfo.isArray() || imageInfo.isEmpty()) {
                    continue;
                }
                JsonNode first = imageInfo.get(0);
                String thumb = text(first, "thumburl");
                String direct = text(first, "url");
                String candidate = !isBlank(thumb) ? thumb : direct;
                if (!isProbableImageUrl(candidate)) {
                    continue;
                }
                int score = scoreWikimediaCandidate(node, candidate, query);
                if (score > bestScore) {
                    bestScore = score;
                    bestUrl = candidate;
                }
            }
            return bestUrl;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int scoreWikimediaCandidate(JsonNode item, String url, String query) {
        String title = safeLower(text(item, "title"));
        String q = safeLower(query);
        int score = 0;

        for (String token : q.split("\\s+")) {
            if (token.length() >= 3 && title.contains(token)) {
                score += 2;
            }
        }

        JsonNode imageInfo = item.path("imageinfo");
        if (imageInfo.isArray() && !imageInfo.isEmpty()) {
            JsonNode first = imageInfo.get(0);
            int width = first.path("thumbwidth").asInt(first.path("width").asInt(0));
            if (width >= 1200) score += 3;
            else if (width >= 800) score += 2;
            else if (width >= 500) score += 1;
        }

        String loweredUrl = safeLower(url);
        if (loweredUrl.contains(".svg") || loweredUrl.contains("icon") || loweredUrl.contains("logo")) {
            score -= 4;
        }
        if (title.contains("logo") || title.contains("icon") || title.contains("symbol")
                || title.contains("vector") || title.contains("illustration")
                || title.contains("meme") || title.contains("poster")) {
            score -= 5;
        }
        if (title.contains("product") || title.contains("item") || title.contains("watch")
                || title.contains("laptop") || title.contains("phone") || title.contains("table")) {
            score += 2;
        }
        return score;
    }

    private String pickImageUrl(JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }
        String url = cleanupOpenverseMediaUrl(text(item, "url"));
        String thumbnail = cleanupOpenverseMediaUrl(text(item, "thumbnail"));
        if (isProbableImageUrl(url)) {
            return url;
        }
        if (isProbableImageUrl(thumbnail)) {
            return thumbnail;
        }
        return url;
    }

    private int scoreCandidate(JsonNode item, String url, String query) {
        int score = 0;

        int width = item.path("width").asInt(0);
        int height = item.path("height").asInt(0);
        if (width >= 1200) score += 4;
        else if (width >= 800) score += 3;
        else if (width >= 500) score += 2;
        if (height >= 500) score += 2;
        else if (height >= 300) score += 1;

        String title = safeLower(text(item, "title"));
        String source = safeLower(text(item, "source"));
        String q = safeLower(query);

        for (String token : q.split("\\s+")) {
            if (token.length() >= 3 && title.contains(token)) {
                score += 2;
            }
        }
        if (source.contains("wikimedia") || source.contains("flickr")) {
            score += 1;
        }

        String loweredUrl = safeLower(url);
        if (loweredUrl.contains(".svg") || loweredUrl.contains("logo") || loweredUrl.contains("icon")) {
            score -= 4;
        }
        if (title.contains("logo") || title.contains("icon") || title.contains("vector")
                || title.contains("illustration") || title.contains("clipart")
                || title.contains("poster") || title.contains("meme")) {
            score -= 5;
        }

        return score;
    }

    private String text(JsonNode item, String field) {
        JsonNode node = item.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeQueryPart(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim()
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s\\-]", " ")
                .replaceAll("\\s{2,}", " ");
        return normalized.trim();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isProbableImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = safeLower(url);
        return u.startsWith("http://") || u.startsWith("https://");
    }

    private boolean isLocalUploadedImage(String url) {
        String u = safeLower(url);
        return u.startsWith("file:/")
                || u.contains("/uploads/marketplace/")
                || u.contains("\\uploads\\marketplace\\");
    }

    private String cleanupOpenverseMediaUrl(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        if (value.isEmpty()) {
            return null;
        }
        value = value.replace("?format=json", "")
                .replace("&format=json", "");
        return value;
    }

    public static final class CheckoutRequest {
        private String firstName;
        private String lastName;
        private String city;
        private String addressLine;
        private String phone;
        private String phone2;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getAddressLine() {
            return addressLine;
        }

        public void setAddressLine(String addressLine) {
            this.addressLine = addressLine;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPhone2() {
            return phone2;
        }

        public void setPhone2(String phone2) {
            this.phone2 = phone2;
        }
    }

    public static final class CheckoutResult {
        private int orderId;
        private String deliveryStatus;
        private String trackingCode;
        private String labelUrl;
        private String deliveryMessage;

        public int getOrderId() {
            return orderId;
        }

        public void setOrderId(int orderId) {
            this.orderId = orderId;
        }

        public String getDeliveryStatus() {
            return deliveryStatus;
        }

        public void setDeliveryStatus(String deliveryStatus) {
            this.deliveryStatus = deliveryStatus;
        }

        public String getTrackingCode() {
            return trackingCode;
        }

        public void setTrackingCode(String trackingCode) {
            this.trackingCode = trackingCode;
        }

        public String getLabelUrl() {
            return labelUrl;
        }

        public void setLabelUrl(String labelUrl) {
            this.labelUrl = labelUrl;
        }

        public String getDeliveryMessage() {
            return deliveryMessage;
        }

        public void setDeliveryMessage(String deliveryMessage) {
            this.deliveryMessage = deliveryMessage;
        }
    }

    @FunctionalInterface
    private interface PreparedStatementConfigurer {
        void configure(PreparedStatement ps) throws SQLException;
    }

    private static final class ListingLock {
        private int idListing;
        private int sellerId;
        private int sourceProjectId;
        private int idRs;
        private int qtyAvailable;
        private double unitPrice;
        private String status;
    }

    private static final class OrderReviewLock {
        private int idOrder;
        private int idListing;
        private int buyerId;
        private String status;
    }
}
