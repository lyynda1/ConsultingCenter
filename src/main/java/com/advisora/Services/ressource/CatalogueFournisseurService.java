package com.advisora.Services.ressource;

import com.advisora.Model.ressource.CatalogueFournisseur;
import com.advisora.utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CatalogueFournisseurService implements ICatalogueFournisseurService {
    @Override
    public void ajouter(CatalogueFournisseur f) {
        validate(f);
        if (existsByName(f.getNomFr(), null)) {
            throw new IllegalArgumentException("Un fournisseur avec ce nom existe deja.");
        }
        String sql = "INSERT INTO cataloguefournisseur (nomFr, quantite, fournisseur, emailFr, localisationFr, numTelFr) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, f.getNomFr().trim());
            ps.setInt(2, f.getQuantite());
            ps.setString(3, nullable(f.getFournisseur()));
            ps.setString(4, nullable(f.getEmailFr()));
            ps.setString(5, nullable(f.getLocalisationFr()));
            ps.setString(6, nullable(f.getNumTelFr()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    f.setIdFr(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur ajout fournisseur: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CatalogueFournisseur> afficher() {
        String sql = "SELECT idFr, nomFr, quantite, fournisseur, emailFr, localisationFr, numTelFr "
                + "FROM cataloguefournisseur ORDER BY idFr DESC";
        List<CatalogueFournisseur> list = new ArrayList<>();
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture fournisseurs: " + e.getMessage(), e);
        }
    }

    @Override
    public void modifier(CatalogueFournisseur f) {
        if (f == null || f.getIdFr() <= 0) {
            throw new IllegalArgumentException("idFr invalide.");
        }
        validate(f);
        if (existsByName(f.getNomFr(), f.getIdFr())) {
            throw new IllegalArgumentException("Un fournisseur avec ce nom existe deja.");
        }
        String sql = "UPDATE cataloguefournisseur SET nomFr = ?, quantite = ?, fournisseur = ?, emailFr = ?, localisationFr = ?, numTelFr = ? "
                + "WHERE idFr = ?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, f.getNomFr().trim());
            ps.setInt(2, f.getQuantite());
            ps.setString(3, nullable(f.getFournisseur()));
            ps.setString(4, nullable(f.getEmailFr()));
            ps.setString(5, nullable(f.getLocalisationFr()));
            ps.setString(6, nullable(f.getNumTelFr()));
            ps.setInt(7, f.getIdFr());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur modification fournisseur: " + e.getMessage(), e);
        }
    }

    @Override
    public void supprimer(CatalogueFournisseur f) {
        if (f == null || f.getIdFr() <= 0) {
            throw new IllegalArgumentException("idFr invalide.");
        }
        String sql = "DELETE FROM cataloguefournisseur WHERE idFr = ?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, f.getIdFr());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erreur suppression fournisseur: " + e.getMessage(), e);
        }
    }

    @Override
    public CatalogueFournisseur getById(int idFr) {
        String sql = "SELECT idFr, nomFr, quantite, fournisseur, emailFr, localisationFr, numTelFr "
                + "FROM cataloguefournisseur WHERE idFr = ?";
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, idFr);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lecture fournisseur: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByName(String name, Integer excludeId) {
        String sql = "SELECT COUNT(*) FROM cataloguefournisseur WHERE LOWER(nomFr) = LOWER(?)"
                + (excludeId != null ? " AND idFr <> ?" : "");
        try (Connection cnx = MyConnection.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, name == null ? "" : name.trim());
            if (excludeId != null) {
                ps.setInt(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur verification fournisseur: " + e.getMessage(), e);
        }
    }

    private void validate(CatalogueFournisseur f) {
        if (f == null) {
            throw new IllegalArgumentException("Fournisseur obligatoire.");
        }
        if (f.getNomFr() == null || f.getNomFr().isBlank()) {
            throw new IllegalArgumentException("Nom fournisseur obligatoire.");
        }
        if (f.getQuantite() < 0) {
            throw new IllegalArgumentException("Quantite >= 0 obligatoire.");
        }
    }

    private String nullable(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private CatalogueFournisseur map(ResultSet rs) throws SQLException {
        CatalogueFournisseur f = new CatalogueFournisseur();
        f.setIdFr(rs.getInt("idFr"));
        f.setNomFr(rs.getString("nomFr"));
        f.setQuantite(rs.getInt("quantite"));
        f.setFournisseur(rs.getString("fournisseur"));
        f.setEmailFr(rs.getString("emailFr"));
        f.setLocalisationFr(rs.getString("localisationFr"));
        f.setNumTelFr(rs.getString("numTelFr"));
        return f;
    }
}
