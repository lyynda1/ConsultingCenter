package com.advisora.Services.ressource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.advisora.Model.ressource.WalletTopup;
import com.advisora.utils.MyConnection;

public class WalletService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAID = "PAID";
    private static final String TYPE_TOPUP = "TOPUP";
    private static final String TYPE_SHOP_BUY = "SHOP_BUY";
    private static final String TYPE_SHOP_SELL = "SHOP_SELL";

    private final ShopPaymentGatewayService paymentGateway = new ShopPaymentGatewayService();
    private volatile boolean schemaReady;
    private volatile Double coinRateCache;

    public double getBalanceCoins(int userId) {
        ensureSchema();
        if (userId <= 0) {
            throw new IllegalArgumentException("Client invalide.");
        }
        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            ensureAccountRow(cnx, userId);
            return readBalance(cnx, userId, false);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture wallet: " + e.getMessage(), e);
        }
    }

    public double estimateCoins(double amountMoney) {
        if (amountMoney <= 0) {
            return 0.0;
        }
        return round3(amountMoney * getCoinRate());
    }

    public WalletTopup createTopupRequest(int userId, String provider, double amountMoney) {
        ensureSchema();
        if (userId <= 0) {
            throw new IllegalArgumentException("Client invalide.");
        }
        if (amountMoney <= 0) {
            throw new IllegalArgumentException("Montant invalide.");
        }

        String normalizedProvider = normalizeProvider(provider);
        double coins = estimateCoins(amountMoney);
        String clientRef = "WLT-" + userId + "-" + System.currentTimeMillis();
        ShopPaymentGatewayService.PaymentInitResult payment = paymentGateway.createPayment(normalizedProvider, amountMoney, clientRef);
        String note = payment.getNote();

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            ensureAccountRow(cnx, userId);
            String sql = "INSERT INTO resource_wallet_topup (idUser, provider, amountMoney, coinAmount, status, externalRef, paymentUrl, note) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setString(2, normalizedProvider);
                ps.setDouble(3, round3(amountMoney));
                ps.setDouble(4, coins);
                ps.setString(5, STATUS_PENDING);
                ps.setString(6, payment.getExternalRef());
                ps.setString(7, payment.getPaymentUrl());
                ps.setString(8, note);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return getTopupById(userId, rs.getInt(1));
                    }
                }
            }
            throw new IllegalStateException("Creation recharge echouee.");
        } catch (SQLException e) {
            throw new RuntimeException("Erreur creation recharge: " + e.getMessage(), e);
        }
    }

    public WalletTopup confirmTopup(int userId, int idTopup) {
        ensureSchema();
        if (userId <= 0 || idTopup <= 0) {
            throw new IllegalArgumentException("Recharge invalide.");
        }

        try (Connection cnx = MyConnection.getInstance().getConnection()) {
            boolean auto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                WalletTopup topup = lockTopup(cnx, userId, idTopup);
                if (topup == null) {
                    throw new IllegalArgumentException("Recharge introuvable.");
                }
                if (STATUS_PAID.equalsIgnoreCase(topup.getStatus())) {
                    cnx.commit();
                    return topup;
                }
                if (!STATUS_PENDING.equalsIgnoreCase(topup.getStatus())) {
                    throw new IllegalStateException("Recharge non confirmable (status: " + topup.getStatus() + ").");
                }

                boolean paid = paymentGateway.verifyPayment(topup.getProvider(), topup.getExternalRef(), topup.getPaymentUrl());
                if (!paid) {
                    throw new IllegalStateException("Paiement non confirme par le provider.");
                }

                try (PreparedStatement ps = cnx.prepareStatement(
                        "UPDATE resource_wallet_topup SET status = ?, confirmedAt = NOW() WHERE idTopup = ?")) {
                    ps.setString(1, STATUS_PAID);
                    ps.setInt(2, idTopup);
                    ps.executeUpdate();
                }

                creditInternal(cnx, userId, topup.getCoinAmount(), TYPE_TOPUP, "TOPUP#" + idTopup + " " + topup.getProvider());
                cnx.commit();
                return getTopupById(userId, idTopup);
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur confirmation recharge: " + e.getMessage(), e);
        }
    }

    public List<WalletTopup> listTopupsForUser(int userId, int limit) {
        ensureSchema();
        if (userId <= 0) {
            throw new IllegalArgumentException("Client invalide.");
        }
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        String sql = "SELECT idTopup, idUser, provider, amountMoney, coinAmount, status, externalRef, paymentUrl, note, createdAt, confirmedAt "
                + "FROM resource_wallet_topup WHERE idUser = ? ORDER BY idTopup DESC LIMIT " + safeLimit;
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<WalletTopup> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapTopup(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture recharges: " + e.getMessage(), e);
        }
    }

    public double debitForShopPurchase(Connection cnx, int userId, double amountCoins, String ref) throws SQLException {
        ensureSchema();
        if (amountCoins <= 0) {
            throw new IllegalArgumentException("Montant wallet invalide.");
        }
        ensureAccountRow(cnx, userId);
        double balance = readBalance(cnx, userId, true);
        double required = round3(amountCoins);
        if (round3(balance + 1e-9) < required) {
            double missing = round3(required - balance);
            throw new IllegalStateException(
                    "Solde insuffisant (" + round3(balance) + " coins). Requis: "
                            + required + " coins, manquant: " + missing + " coins."
            );
        }
        double next = round3(balance - amountCoins);
        writeBalance(cnx, userId, next);
        insertTxn(cnx, userId, TYPE_SHOP_BUY, -round3(amountCoins), next, ref);
        return next;
    }

    public double creditForShopSale(Connection cnx, int userId, double amountCoins, String ref) throws SQLException {
        ensureSchema();
        if (amountCoins <= 0) {
            return getBalanceCoins(userId);
        }
        ensureAccountRow(cnx, userId);
        return creditInternal(cnx, userId, amountCoins, TYPE_SHOP_SELL, ref);
    }

    private double creditInternal(Connection cnx, int userId, double amountCoins, String txnType, String ref) throws SQLException {
        ensureAccountRow(cnx, userId);
        double balance = readBalance(cnx, userId, true);
        double next = round3(balance + amountCoins);
        writeBalance(cnx, userId, next);
        insertTxn(cnx, userId, txnType, round3(amountCoins), next, ref);
        return next;
    }

    private void writeBalance(Connection cnx, int userId, double balance) throws SQLException {
        try (PreparedStatement ps = cnx.prepareStatement("UPDATE resource_wallet_account SET balanceCoins = ?, updatedAt = NOW() WHERE idUser = ?")) {
            ps.setDouble(1, round3(balance));
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private void insertTxn(Connection cnx, int userId, String txnType, double amount, double balanceAfter, String ref) throws SQLException {
        try (PreparedStatement ps = cnx.prepareStatement(
                "INSERT INTO resource_wallet_txn (idUser, txnType, amountCoins, balanceAfter, ref) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, txnType);
            ps.setDouble(3, round3(amount));
            ps.setDouble(4, round3(balanceAfter));
            ps.setString(5, safe(ref));
            ps.executeUpdate();
        }
    }

    private WalletTopup getTopupById(int userId, int idTopup) {
        String sql = "SELECT idTopup, idUser, provider, amountMoney, coinAmount, status, externalRef, paymentUrl, note, createdAt, confirmedAt "
                + "FROM resource_wallet_topup WHERE idUser = ? AND idTopup = ?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, idTopup);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTopup(rs);
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture recharge: " + e.getMessage(), e);
        }
    }

    private WalletTopup lockTopup(Connection cnx, int userId, int idTopup) throws SQLException {
        String sql = "SELECT idTopup, idUser, provider, amountMoney, coinAmount, status, externalRef, paymentUrl, note, createdAt, confirmedAt "
                + "FROM resource_wallet_topup WHERE idUser = ? AND idTopup = ? FOR UPDATE";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, idTopup);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTopup(rs);
                }
                return null;
            }
        }
    }

    private WalletTopup mapTopup(ResultSet rs) throws SQLException {
        WalletTopup t = new WalletTopup();
        t.setIdTopup(rs.getInt("idTopup"));
        t.setUserId(rs.getInt("idUser"));
        t.setProvider(rs.getString("provider"));
        t.setAmountMoney(rs.getDouble("amountMoney"));
        t.setCoinAmount(rs.getDouble("coinAmount"));
        t.setStatus(rs.getString("status"));
        t.setExternalRef(rs.getString("externalRef"));
        t.setPaymentUrl(rs.getString("paymentUrl"));
        t.setNote(rs.getString("note"));
        t.setCreatedAt(rs.getTimestamp("createdAt"));
        t.setConfirmedAt(rs.getTimestamp("confirmedAt"));
        return t;
    }

    private void ensureAccountRow(Connection cnx, int userId) throws SQLException {
        try (PreparedStatement ps = cnx.prepareStatement(
                "INSERT INTO resource_wallet_account (idUser, balanceCoins) VALUES (?, 0) "
                        + "ON DUPLICATE KEY UPDATE idUser = idUser")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    private double readBalance(Connection cnx, int userId, boolean forUpdate) throws SQLException {
        String sql = "SELECT balanceCoins FROM resource_wallet_account WHERE idUser = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0.0;
                }
                return round3(rs.getDouble(1));
            }
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
                        CREATE TABLE IF NOT EXISTS resource_wallet_account (
                            idUser INT PRIMARY KEY,
                            balanceCoins DECIMAL(14,3) NOT NULL DEFAULT 0,
                            updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            INDEX idx_rwa_updated (updatedAt)
                        )
                        """);

                st.execute("""
                        CREATE TABLE IF NOT EXISTS resource_wallet_topup (
                            idTopup INT AUTO_INCREMENT PRIMARY KEY,
                            idUser INT NOT NULL,
                            provider VARCHAR(20) NOT NULL,
                            amountMoney DECIMAL(14,3) NOT NULL,
                            coinAmount DECIMAL(14,3) NOT NULL,
                            status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                            externalRef VARCHAR(140) NULL,
                            paymentUrl VARCHAR(700) NULL,
                            note VARCHAR(255) NULL,
                            createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            confirmedAt TIMESTAMP NULL DEFAULT NULL,
                            INDEX idx_rwt_user (idUser),
                            INDEX idx_rwt_status (status)
                        )
                        """);

                st.execute("""
                        CREATE TABLE IF NOT EXISTS resource_wallet_txn (
                            idTxn INT AUTO_INCREMENT PRIMARY KEY,
                            idUser INT NOT NULL,
                            txnType VARCHAR(30) NOT NULL,
                            amountCoins DECIMAL(14,3) NOT NULL,
                            balanceAfter DECIMAL(14,3) NOT NULL,
                            ref VARCHAR(180) NULL,
                            createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            INDEX idx_rwx_user (idUser),
                            INDEX idx_rwx_type (txnType)
                        )
                        """);

                schemaReady = true;
            } catch (SQLException e) {
                throw new RuntimeException("Erreur initialisation wallet: " + e.getMessage(), e);
            }
        }
    }

    private double getCoinRate() {
        if (coinRateCache != null) {
            return coinRateCache;
        }
        synchronized (this) {
            if (coinRateCache != null) {
                return coinRateCache;
            }
            String env = System.getenv("SHOP_COIN_RATE");
            if (env == null || env.trim().isEmpty()) {
                env = readFromDotEnv("SHOP_COIN_RATE");
            }
            double rate = 10.0;
            if (env != null && !env.trim().isEmpty()) {
                try {
                    rate = Double.parseDouble(env.trim());
                } catch (NumberFormatException ignored) {
                    rate = 10.0;
                }
            }
            if (rate <= 0) {
                rate = 10.0;
            }
            coinRateCache = rate;
            return coinRateCache;
        }
    }

    private String readFromDotEnv(String key) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(".env");
            if (!java.nio.file.Files.exists(p)) {
                return null;
            }
            for (String raw : java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8)) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = line.substring(0, idx).trim();
                if (!k.equals(key)) {
                    continue;
                }
                String v = line.substring(idx + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                return v;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return "FLOUCI";
        }
        String p = provider.trim().toUpperCase();
        if (p.startsWith("STRIPE")) {
            return "STRIPE";
        }
        if (p.startsWith("D17")) {
            return "D17";
        }
        return "FLOUCI";
    }

    private String safe(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 180) {
            return normalized.substring(0, 180);
        }
        return normalized;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
