package com.advisora.Services.user;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.advisora.utils.MyConnection;
import java.sql.*;
import java.time.LocalDateTime;
import com.advisora.utils.TokenUtil;


public class AuthSessionService {

    // 7 days
    private static final int SESSION_DAYS = 7;

    public boolean isSessionValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;

        String tokenHash = sha256(rawToken);

        String sql = """
            SELECT id
            FROM auth_session
            WHERE token_hash=? AND revoked_at IS NULL AND expires_at > NOW()
            LIMIT 1
        """;

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                boolean ok = rs.next();
                if (ok) touch(tokenHash);
                return ok;
            }
        } catch (SQLException e) {
            throw new RuntimeException("isSessionValid failed: " + e.getMessage(), e);
        }
    }

    public Integer getUserIdByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        String tokenHash = sha256(rawToken);

        String sql = """
            SELECT user_id
            FROM auth_session
            WHERE token_hash=? AND revoked_at IS NULL AND expires_at > NOW()
            LIMIT 1
        """;
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("user_id") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getUserIdByToken failed: " + e.getMessage(), e);
        }
    }

    public void createSession(int userId, String rawToken, String deviceName, String ip) {
        String tokenHash = sha256(rawToken);
        LocalDateTime expires = LocalDateTime.now().plusDays(SESSION_DAYS);

        String sql = """
            INSERT INTO auth_session(user_id, token_hash, device_name, expires_at, last_seen_at, ip_address)
            VALUES(?, ?, ?, ?, NOW(), ?)
        """;

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, tokenHash);
            ps.setString(3, deviceName);
            ps.setTimestamp(4, Timestamp.valueOf(expires));
            ps.setString(5, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createSession failed: " + e.getMessage(), e);
        }
    }

    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        String tokenHash = sha256(rawToken);

        String sql = "UPDATE auth_session SET revoked_at=NOW() WHERE token_hash=? AND revoked_at IS NULL";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("revokeSession failed: " + e.getMessage(), e);
        }
    }

    private void touch(String tokenHash) {
        String sql = "UPDATE auth_session SET last_seen_at=NOW() WHERE token_hash=?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
    private String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha256 failed: " + e.getMessage(), e);
        }
    }
}