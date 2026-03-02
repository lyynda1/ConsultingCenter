package com.advisora.Services.user;

import com.advisora.Model.user.User;
import com.advisora.Services.IService;
import com.advisora.enums.UserRole;
import com.advisora.utils.MyConnection;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserService implements IService<User> {

    public void ensureDefaultAdminAccount(String email, String plainPassword) {
        String adminEmail = (email == null) ? "" : email.trim();
        String adminPassword = (plainPassword == null) ? "" : plainPassword.trim();
        if (adminEmail.isEmpty() || adminPassword.isEmpty()) return;

        String findSql = "SELECT idUser, roleUser FROM `user` WHERE EmailUser=? LIMIT 1";
        String insertSql = "INSERT INTO `user` " +
                "(cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser, roleUser, expertiseAreaUser, image_path, face_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String promoteSql = "UPDATE `user` SET roleUser='admin' WHERE idUser=?";

        try (Connection conn = MyConnection.getInstance().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(findSql)) {
                ps.setString(1, adminEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UserRole role = fromDbRole(rs.getString("roleUser"));
                        if (role != UserRole.ADMIN) {
                            try (PreparedStatement up = conn.prepareStatement(promoteSql)) {
                                up.setInt(1, rs.getInt("idUser"));
                                up.executeUpdate();
                            }
                        }
                        return;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, "00000000");
                ps.setString(2, adminEmail);
                ps.setString(3, BCrypt.hashpw(adminPassword, BCrypt.gensalt(12)));
                ps.setString(4, "Admin");
                ps.setString(5, "System");
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.DATE);
                ps.setString(8, "admin");
                ps.setNull(9, Types.VARCHAR);
                ps.setNull(10, Types.VARCHAR);
                ps.setNull(11, Types.VARCHAR);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("ensureDefaultAdminAccount failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void ajouter(User u) {
        String sql = "INSERT INTO `user` " +
                "(cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser, roleUser, expertiseAreaUser, image_path, face_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, u.getCin());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPassword());
            ps.setString(4, u.getNom());
            ps.setString(5, u.getPrenom());
            ps.setString(6, u.getNumTel());

            if (u.getDateN() == null || u.getDateN().trim().isEmpty()) ps.setNull(7, Types.DATE);
            else ps.setDate(7, Date.valueOf(u.getDateN()));

            ps.setString(8, u.getRole() != null ? toDbRole(u.getRole()) : "client");
            ps.setString(9, u.getExpertiseArea());

            // profile image
            ps.setString(10, u.getImagePath());

            // enrolled face image (new)
            // If you didnâ€™t add facePath in User model yet, add it (I can give it too)
            ps.setString(11, u.getFacePath());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) u.setId(rs.getInt(1));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout user: " + e.getMessage(), e);
        }
    }

    // =============== SORTED BY ROLE ===============
    public List<User> afficherSortedByRole(boolean asc) {
        String order = asc ? "ASC" : "DESC";
        String sql = "SELECT idUser, cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser, roleUser, expertiseAreaUser, image_path, face_path " +
                "FROM `user` ORDER BY roleUser " + order;

        List<User> list = new ArrayList<>();

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapUser(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur afficherSortedByRole: " + e.getMessage(), e);
        }

        return list;
    }

    // =============== GET BY EMAIL ===============
    public User getByEmail(String email) {
        String sql = "SELECT idUser, cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser, roleUser, expertiseAreaUser, image_path, face_path " +
                "FROM `user` WHERE EmailUser = ? LIMIT 1";

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapUser(rs);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur getByEmail: " + e.getMessage(), e);
        }
    }

    // =============== LIST ALL ===============
    @Override
    public List<User> afficher() {
        String sql = "SELECT idUser, cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser, roleUser, expertiseAreaUser, image_path, face_path " +
                "FROM `user`";

        List<User> list = new ArrayList<>();

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapUser(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur afficher users: " + e.getMessage(), e);
        }

        return list;
    }
    public void updatePasswordHashed(int userId, String passwordHash) {

        int days = getDaysSincePasswordChange(userId);
        if (days < 14) {
            int remaining = 14 - days;
            throw new RuntimeException("Mot de passe changÃ© rÃ©cemment. RÃ©essayez dans " + remaining + " jours.");
        }

        String sql = "UPDATE `user` SET passwordUser=?, password_changed_at=NOW() WHERE idUser=?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur update password: " + e.getMessage(), e);
        }
    }
    // =============== UPDATE PROFILE IMAGE ===============
    public void updateImagePath(int userId, String path) {
        String sql = "UPDATE `user` SET image_path=? WHERE idUser=?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur updateImagePath: " + e.getMessage(), e);
        }
    }

    // =============== UPDATE FACE PATH (NEW) ===============
    public int updateFacePath(int userId, String path) {
        String sql = "UPDATE `user` SET face_path=? WHERE idUser=?";
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setInt(2, userId);
            return ps.executeUpdate(); // âœ… returns number of rows updated
        } catch (SQLException e) {
            throw new RuntimeException("Erreur updateFacePath: " + e.getMessage(), e);
        }
    }

    // =============== UPDATE USER ===============
    @Override
    public void modifier(User u) {

        boolean updatePassword = (u.getPassword() != null && !u.getPassword().trim().isEmpty());

        // We update BOTH image_path and face_path here.
        String sql;
        if (updatePassword) {
            sql = "UPDATE `user` SET cin=?, EmailUser=?, passwordUser=?, nomUser=?, PrenomUser=?, NumTelUser=?, dateNUser=?, roleUser=?, expertiseAreaUser=?, image_path=?, face_path=? WHERE idUser=?";
        } else {
            sql = "UPDATE `user` SET cin=?, EmailUser=?, nomUser=?, PrenomUser=?, NumTelUser=?, dateNUser=?, roleUser=?, expertiseAreaUser=?, image_path=?, face_path=? WHERE idUser=?";
        }

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = 1;

            ps.setString(i++, u.getCin());
            ps.setString(i++, u.getEmail());

            if (updatePassword) {
                ps.setString(i++, hashPassword(u.getPassword()));
            }

            ps.setString(i++, u.getNom());
            ps.setString(i++, u.getPrenom());
            ps.setString(i++, u.getNumTel());

            if (u.getDateN() == null || u.getDateN().trim().isEmpty())
                ps.setNull(i++, Types.DATE);
            else
                ps.setDate(i++, Date.valueOf(u.getDateN()));

            ps.setString(i++, u.getRole() != null ? toDbRole(u.getRole()) : "client");
            ps.setString(i++, u.getExpertiseArea());

            ps.setString(i++, u.getImagePath());
            ps.setString(i++, u.getFacePath());

            ps.setInt(i, u.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur modifier user: " + e.getMessage(), e);
        }
    }

    // =============== DELETE ===============
    @Override
    public void supprimer(User u) {
        supprimerParId(u.getId());
    }

    public void supprimerParId(int id) {
        String sql = "DELETE FROM `user` WHERE idUser=?";

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur supprimer user: " + e.getMessage(), e);
        }
    }

    // =============== AUTHENTICATE ===============
    public User authenticate(String email, String password) {

        String sql = """
        SELECT idUser, cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser, dateNUser,
               roleUser, expertiseAreaUser, image_path, face_path,
               failed_login_count, lock_until
        FROM `user`
        WHERE EmailUser = ?
        LIMIT 1
    """;

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Timestamp lockUntil = rs.getTimestamp("lock_until");
                if (lockUntil != null && lockUntil.toInstant().isAfter(java.time.Instant.now())) {
                    // locked
                    return null;
                }

                String hash = rs.getString("passwordUser");
                if (hash == null) return null;

                boolean ok = hash.startsWith("$2a$") || hash.startsWith("$2b$")
                        ? org.mindrot.jbcrypt.BCrypt.checkpw(password, hash)
                        : password.equals(hash);

                if (!ok) {
                    // increment fail and maybe lock
                    registerLoginFailure(email, 10); // 10 min (ou 15)
                    return null;
                }

                // success -> reset counters + last activity
                User u = mapUser(rs);
                registerLoginSuccess(u.getId());
                return u;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur login user: " + e.getMessage(), e);
        }
    }
    public int getDaysSincePasswordChange(int userId) {
        String sql = "SELECT TIMESTAMPDIFF(DAY, password_changed_at, NOW()) AS days FROM `user` WHERE idUser=?";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 9999; // if no user
                int days = rs.getInt("days");
                // if password_changed_at is NULL, days will be 0 sometimes depending on MySQL -> handle it:
                Timestamp t = null;
                try (PreparedStatement ps2 = c.prepareStatement("SELECT password_changed_at FROM `user` WHERE idUser=?")) {
                    ps2.setInt(1, userId);
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (rs2.next()) t = rs2.getTimestamp("password_changed_at");
                    }
                }
                if (t == null) return 9999; // never changed -> allowed
                return days;
            }
        } catch (Exception e) {
            return 9999;
        }
    }
    public User getById(int id) {
        String sql = """
        SELECT idUser, cin, EmailUser, passwordUser, nomUser, PrenomUser, NumTelUser,
               dateNUser, roleUser, expertiseAreaUser, image_path, face_path, totp_secret, totp_enabled
        FROM user
        WHERE idUser = ?
        LIMIT 1
    """;

        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapUser(rs);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur getById: " + e.getMessage(), e);
        }
    }
    public boolean isLocked(String email) {
        String sql = "SELECT lock_until FROM `user` WHERE EmailUser=? LIMIT 1";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                Timestamp lockUntil = rs.getTimestamp("lock_until");
                return lockUntil != null && lockUntil.toInstant().isAfter(java.time.Instant.now());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public int registerLoginFailure(String email, int lockMinutes) {
        // returns failed_login_count after update
        String sqlUpdate = """
        UPDATE `user`
        SET failed_login_count = failed_login_count + 1,
            lock_until = CASE
                WHEN failed_login_count + 1 >= 3 THEN DATE_ADD(NOW(), INTERVAL ? MINUTE)
                ELSE lock_until
            END
        WHERE EmailUser=?
    """;

        String sqlGet = "SELECT failed_login_count FROM `user` WHERE EmailUser=? LIMIT 1";

        try (Connection c = MyConnection.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sqlUpdate)) {
                ps.setInt(1, lockMinutes);
                ps.setString(2, email);
                ps.executeUpdate();
            }
            try (PreparedStatement ps2 = c.prepareStatement(sqlGet)) {
                ps2.setString(1, email);
                try (ResultSet rs = ps2.executeQuery()) {
                    if (!rs.next()) return 0;
                    return rs.getInt("failed_login_count");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerLoginSuccess(int userId) {
        String sql = """
        UPDATE `user`
        SET failed_login_count=0,
            lock_until=NULL,
            last_activity_at=NOW()
        WHERE idUser=?
    """;
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public long getLockRemainingSeconds(String email) {
        String sql = "SELECT TIMESTAMPDIFF(SECOND, NOW(), lock_until) AS sec FROM `user` WHERE EmailUser=? LIMIT 1";
        try (Connection c = MyConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                long sec = rs.getLong("sec");
                return Math.max(0, sec);
            }
        } catch (Exception e) {
            return 0;
        }
    }
    public List<User> getUsersWithFacePath() {
        String sql = """
        SELECT idUser, EmailUser, face_path
        FROM `user`
        WHERE face_path IS NOT NULL AND TRIM(face_path) <> ''
    """;

        List<User> list = new ArrayList<>();
        try (Connection conn = MyConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("idUser"));
                u.setEmail(rs.getString("EmailUser"));
                u.setFacePath(rs.getString("face_path"));
                list.add(u);
            }
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("getUsersWithFacePath failed", e);
        }
    }
    // =============== MAPPER (avoids duplication) ===============
    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("idUser"));
        u.setCin(rs.getString("cin"));
        u.setEmail(rs.getString("EmailUser"));
        u.setPassword(rs.getString("passwordUser"));
        u.setNom(rs.getString("nomUser"));
        u.setPrenom(rs.getString("PrenomUser"));
        u.setNumTel(rs.getString("NumTelUser"));

        Date d = rs.getDate("dateNUser");
        u.setDateN(d != null ? d.toString() : null);

        String roleDb = rs.getString("roleUser");
        u.setRole(roleDb != null ? fromDbRole(roleDb) : null);

        u.setExpertiseArea(rs.getString("expertiseAreaUser"));

        // profile pic
        u.setImagePath(rs.getString("image_path"));

        // face enrollment pic (new)
        u.setFacePath(rs.getString("face_path"));

        return u;
    }

    // --- helpers role ---
    private String toDbRole(UserRole role) {
        if (role == null) return "client";
        return switch (role) {
            case CLIENT -> "client";
            case GERANT -> "gerant";
            case ADMIN -> "admin";
        };
    }

    private UserRole fromDbRole(String dbRole) {
        if (dbRole == null || dbRole.trim().isEmpty()) return UserRole.CLIENT;

        String normalized = Normalizer.normalize(dbRole, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "client", "role_client" -> UserRole.CLIENT;
            case "gerant", "manager", "role_gerant", "role_manager" -> UserRole.GERANT;
            case "admin", "role_admin" -> UserRole.ADMIN;
            default -> UserRole.CLIENT;
        };
    }
    private String hashPassword(String plain) {
        if (plain == null) return null;
        String p = plain.trim();
        if (p.isEmpty()) return null;

        // If already hashed (avoid double-hash)
        if (p.startsWith("$2a$") || p.startsWith("$2b$") || p.startsWith("$2y$")) return p;

        return BCrypt.hashpw(p, BCrypt.gensalt(12)); // 12 rounds is a good default
    }
}
