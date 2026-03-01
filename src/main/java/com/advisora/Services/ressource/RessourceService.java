package com.advisora.Services.ressource;

import com.advisora.Model.ressource.Ressource;
import com.advisora.enums.RessourceStatut;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class RessourceService implements IRessourceService {
    private String qtyColumnCache;

    @Override
    public void ajouter(Ressource r) {
        validate(r);
        String sql = "INSERT INTO resources (nomRs, prixRs, QuantiteRs, availabilityStatusRs, idFr) VALUES (?, ?, ?, ?, ?)";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getNomRs().trim());
            ps.setDouble(2, r.getPrixRs());
            ps.setInt(3, r.getQuantiteRs());
            ps.setString(4, r.getAvailabilityStatusRs().name());
            ps.setInt(5, r.getIdFr());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    r.setIdRs(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout ressource: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Ressource> afficher() {
        String sql = "SELECT idRs, nomRs, prixRs, QuantiteRs, availabilityStatusRs, idFr FROM resources ORDER BY idRs DESC";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Ressource> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture ressources: " + e.getMessage(), e);
        }
    }

    @Override
    public void modifier(Ressource r) {
        if (r == null || r.getIdRs() <= 0) {
            throw new IllegalArgumentException("idRs invalide.");
        }
        validate(r);

        String sql = "UPDATE resources SET nomRs=?, prixRs=?, QuantiteRs=?, availabilityStatusRs=?, idFr=? WHERE idRs=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, r.getNomRs().trim());
            ps.setDouble(2, r.getPrixRs());
            ps.setInt(3, r.getQuantiteRs());
            ps.setString(4, r.getAvailabilityStatusRs().name());
            ps.setInt(5, r.getIdFr());
            ps.setInt(6, r.getIdRs());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification ressource: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(Ressource r) {
        if (r == null || r.getIdRs() <= 0) {
            throw new IllegalArgumentException("idRs invalide.");
        }
        String sql = "DELETE FROM resources WHERE idRs=?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, r.getIdRs());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression ressource: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Ressource> getByFournisseur(int idFr) {
        String sql = "SELECT idRs, nomRs, prixRs, QuantiteRs, availabilityStatusRs, idFr FROM resources WHERE idFr = ? ORDER BY idRs DESC";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idFr);
            try (ResultSet rs = ps.executeQuery()) {
                List<Ressource> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture ressources fournisseur: " + e.getMessage(), e);
        }
    }

    @Override
    public Ressource getById(int idRs) {
        String sql = "SELECT idRs, nomRs, prixRs, QuantiteRs, availabilityStatusRs, idFr FROM resources WHERE idRs = ?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idRs);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture ressource: " + e.getMessage(), e);
        }
    }

    @Override
    public int getReservedStock(int idRs) {
        String qtyColumn = detectProjectResourcesQuantityColumn();
        String sql;
        if (qtyColumn == null) {
            sql = "SELECT COUNT(*) FROM project_resources WHERE idRs = ?";
        } else {
            sql = "SELECT COALESCE(SUM(" + qtyColumn + "), 0) FROM project_resources WHERE idRs = ?";
        }

        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idRs);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur calcul stock reserve: " + e.getMessage(), e);
        }
    }

    @Override
    public int getAvailableStock(int idRs) {
        Ressource r = getById(idRs);
        if (r == null) {
            return 0;
        }
        return Math.max(0, r.getQuantiteRs() - getReservedStock(idRs));
    }

    @Override
    public Map<Integer, Integer> getReservedStockBulk(List<Integer> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Integer> ids = resourceIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        StringJoiner placeholders = new StringJoiner(",");
        for (int i = 0; i < ids.size(); i++) {
            placeholders.add("?");
        }

        String qtyColumn = detectProjectResourcesQuantityColumn();
        String reservedExpr = qtyColumn == null ? "COUNT(*)" : "COALESCE(SUM(" + qtyColumn + "), 0)";
        String sql = "SELECT idRs, " + reservedExpr + " AS reserved "
                + "FROM project_resources "
                + "WHERE idRs IN (" + placeholders + ") "
                + "GROUP BY idRs";

        Map<Integer, Integer> reservedMap = new HashMap<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reservedMap.put(rs.getInt("idRs"), rs.getInt("reserved"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur calcul stock reserve bulk: " + e.getMessage(), e);
        }

        for (Integer id : ids) {
            reservedMap.putIfAbsent(id, 0);
        }
        return reservedMap;
    }

    @Override
    public Map<Integer, Integer> getAvailableStockBulk(List<Ressource> resources, Map<Integer, Integer> reservedMap) {
        if (resources == null || resources.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Integer> availableMap = new HashMap<>();
        for (Ressource r : resources) {
            if (r == null || r.getIdRs() <= 0) {
                continue;
            }
            int reserved = reservedMap == null ? 0 : reservedMap.getOrDefault(r.getIdRs(), 0);
            availableMap.put(r.getIdRs(), Math.max(0, r.getQuantiteRs() - reserved));
        }
        return availableMap;
    }

    public Map<Integer, String> getProjectTitlesByResourceIds(List<Integer> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Integer> ids = resourceIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        StringJoiner placeholders = new StringJoiner(",");
        for (int i = 0; i < ids.size(); i++) {
            placeholders.add("?");
        }

        String sql = "SELECT pr.idRs, GROUP_CONCAT(DISTINCT p.titleProj ORDER BY p.titleProj SEPARATOR ' | ') AS projectTitles "
                + "FROM project_resources pr "
                + "JOIN projects p ON p.idProj = pr.idProj "
                + "WHERE pr.idRs IN (" + placeholders + ") "
                + "GROUP BY pr.idRs";

        Map<Integer, String> result = new HashMap<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("idRs");
                    String titles = rs.getString("projectTitles");
                    result.put(id, titles == null ? "" : titles.trim());
                }
            }
        } catch (SQLException e) {
            // Recherche par projet = enrichissement; fallback silencieux si table/colonne indisponible.
            return Collections.emptyMap();
        }

        for (Integer id : ids) {
            result.putIfAbsent(id, "");
        }
        return result;
    }

    private Ressource map(ResultSet rs) throws SQLException {
        Ressource r = new Ressource();
        r.setIdRs(rs.getInt("idRs"));
        r.setNomRs(rs.getString("nomRs"));
        r.setPrixRs(rs.getDouble("prixRs"));
        r.setQuantiteRs(rs.getInt("QuantiteRs"));
        r.setAvailabilityStatusRs(RessourceStatut.fromDb(rs.getString("availabilityStatusRs")));
        r.setIdFr(rs.getInt("idFr"));
        return r;
    }

    private void validate(Ressource r) {
        if (r == null) {
            throw new IllegalArgumentException("Ressource obligatoire.");
        }
        if (r.getNomRs() == null || r.getNomRs().isBlank()) {
            throw new IllegalArgumentException("Nom ressource obligatoire.");
        }
        if (r.getPrixRs() < 0) {
            throw new IllegalArgumentException("Prix >= 0 obligatoire.");
        }
        if (r.getQuantiteRs() < 0) {
            throw new IllegalArgumentException("Quantite >= 0 obligatoire.");
        }
        if (r.getIdFr() <= 0) {
            throw new IllegalArgumentException("Fournisseur obligatoire.");
        }
        if (r.getAvailabilityStatusRs() == null) {
            r.setAvailabilityStatusRs(r.getQuantiteRs() > 0 ? RessourceStatut.AVAILABLE : RessourceStatut.UNAVAILABLE);
        }
    }

    private String detectProjectResourcesQuantityColumn() {
        if (qtyColumnCache != null) {
            return qtyColumnCache.equals("__NONE__") ? null : qtyColumnCache;
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
}

