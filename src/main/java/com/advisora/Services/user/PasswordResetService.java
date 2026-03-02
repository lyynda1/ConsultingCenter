package com.advisora.Services.user;

import com.advisora.Model.user.User;
import com.advisora.utils.EmailSender;
import com.advisora.utils.MyConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;

public class PasswordResetService {

    private final UserService userService = new UserService();
    private final SecureRandom rnd = new SecureRandom();

    // Configure once (or load from config/env)
    private final EmailSender mailer = new EmailSender(
            "smtp.gmail.com", 587,
            "lyynda19@gmaiL.com",
            "bsiy vjdy yaep ikom"
    );

    public boolean requestCode(String email) {
        email = safe(email);
        if (email.isEmpty()) return false;

        User u = userService.getByEmail(email);
        if (u == null) {
            // Security: donâ€™t reveal if email exists
            return false;
        }

        String code = generate6DigitCode();
        String codeHash = BCrypt.hashpw(code, BCrypt.gensalt(10));
        LocalDateTime expires = LocalDateTime.now().plusMinutes(10);

        try (Connection conn = MyConnection.getInstance().getConnection()) {
            // Optional: invalidate older unused codes
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE password_reset SET used_at=NOW() WHERE user_id=? AND used_at IS NULL"
            )) {
                ps.setInt(1, u.getId());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO password_reset(user_id, code_hash, expires_at) VALUES(?,?,?)"
            )) {
                ps.setInt(1, u.getId());
                ps.setString(2, codeHash);
                ps.setTimestamp(3, Timestamp.valueOf(expires));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("requestCode failed: " + e.getMessage(), e);
        }

        String subject = "Advisora - Password Reset Code";
        String body =
                "Your verification code is: " + code + "\n\n" +
                        "This code expires in 10 minutes.\n" +
                        "If you did not request this, ignore this email.";

        mailer.send(email, subject, body);
        return true;
    }

    public boolean verifyAndReset(String email, String code, String newPassword) {
        email = safe(email);
        code = safe(code);
        newPassword = safe(newPassword);

        if (email.isEmpty() || code.isEmpty() || newPassword.isEmpty()) return false;

        User u = userService.getByEmail(email);
        if (u == null) return false;

        try (Connection conn = MyConnection.getInstance().getConnection()) {

            // get latest unused code
            int resetId = -1;
            String codeHash = null;
            Timestamp expiresAt = null;
            int attempts = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT id, code_hash, expires_at, attempts
                    FROM password_reset
                    WHERE user_id=? AND used_at IS NULL
                    ORDER BY created_at DESC
                    LIMIT 1
                    """
            )) {
                ps.setInt(1, u.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    resetId = rs.getInt("id");
                    codeHash = rs.getString("code_hash");
                    expiresAt = rs.getTimestamp("expires_at");
                    attempts = rs.getInt("attempts");
                }
            }

            if (expiresAt == null || expiresAt.before(new Timestamp(System.currentTimeMillis()))) {
                markUsed(conn, resetId); // expire it
                return false;
            }

            if (attempts >= 5) {
                markUsed(conn, resetId);
                return false;
            }

            boolean ok = BCrypt.checkpw(code, codeHash);

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE password_reset SET attempts=attempts+1 WHERE id=?"
            )) {
                ps.setInt(1, resetId);
                ps.executeUpdate();
            }

            if (!ok) return false;

            // âœ… Update password
            String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(10));
            userService.updatePasswordHashed(u.getId(), hashed);

            // mark code used
            markUsed(conn, resetId);
            return true;

        } catch (SQLException e) {
            throw new RuntimeException("verifyAndReset failed: " + e.getMessage(), e);
        }
    }

    private void markUsed(Connection conn, int resetId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE password_reset SET used_at=NOW() WHERE id=?"
        )) {
            ps.setInt(1, resetId);
            ps.executeUpdate();
        }
    }

    private String generate6DigitCode() {
        int x = rnd.nextInt(900000) + 100000;
        return String.valueOf(x);
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
}
