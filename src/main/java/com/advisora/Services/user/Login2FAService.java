package com.advisora.Services.user;

import com.advisora.utils.MyConnection;
import com.advisora.utils.EmailSender;

import java.sql.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Random;

public class Login2FAService {

    private static final int OTP_MINUTES = 10;

    // ✅ configure ton sender une seule fois
    private final EmailSender sender = new EmailSender(
            "smtp.gmail.com",
            587,
            "lyynda19@gmail.com",
            "bsiy vjdy yaep ikom" // mot de passe d’application
    );

    public void requestLoginCode(String email) {
        String code = generate6Digits();
        String codeHash = sha256(code);
        LocalDateTime expires = LocalDateTime.now().plusMinutes(OTP_MINUTES);

        String invalidateSql = """
            UPDATE otp_code
            SET used_at = NOW()
            WHERE email=? AND purpose='LOGIN' AND used_at IS NULL
        """;

        String insertSql = """
    INSERT INTO otp_code(email, purpose, code_hash, expires_at)
    VALUES(?, 'LOGIN', ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))
""";
        try (Connection conn = MyConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps1 = conn.prepareStatement(invalidateSql)) {
                ps1.setString(1, email);
                ps1.executeUpdate();
            }

            try (PreparedStatement ps2 = conn.prepareStatement(insertSql)) {
                ps2.setString(1, email);
                ps2.setString(2, codeHash);
                ps2.setInt(3, OTP_MINUTES);
                ps2.executeUpdate();
            }
            conn.commit();

        } catch (Exception e) {
            throw new RuntimeException("requestLoginCode failed: " + e.getMessage(), e);
        }

        // ✅ send email (instance method)
        String subject = "Advisora login verification code";
        String body = "Your login code is: " + code + "\n\nThis code expires in " + OTP_MINUTES + " minutes.";
        sender.send(email, subject, body);
    }

    public boolean verifyLoginCode(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) return false;

        String codeHash = sha256(code.trim());

        String sql = """
            SELECT id
            FROM otp_code
            WHERE email=? AND purpose='LOGIN'
              AND code_hash=? AND used_at IS NULL
              AND expires_at > NOW()
            ORDER BY id DESC
            LIMIT 1
        """;

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, codeHash);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;

                int id = rs.getInt("id");

                try (PreparedStatement ps2 = conn.prepareStatement(
                        "UPDATE otp_code SET used_at=NOW() WHERE id=?")) {
                    ps2.setInt(1, id);
                    ps2.executeUpdate();
                }

                return true;
            }

        } catch (SQLException e) {
            throw new RuntimeException("verifyLoginCode failed: " + e.getMessage(), e);
        }
    }

    private String generate6Digits() {
        int n = 100000 + new Random().nextInt(900000);
        return String.valueOf(n);
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha256 failed", e);
        }
    }
}