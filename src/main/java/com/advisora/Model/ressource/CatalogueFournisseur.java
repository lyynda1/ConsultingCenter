package com.advisora.Model.ressource;

public class CatalogueFournisseur {
    private int idFr;
    private String nomFr;
    private int quantite;
    private String fournisseur;
    private String emailFr;
    private String localisationFr;
    private String numTelFr;

    public int getIdFr() {
        return idFr;
    }

    public void setIdFr(int idFr) {
        this.idFr = idFr;
    }

    public String getNomFr() {
        return nomFr;
    }

    public void setNomFr(String nomFr) {
        this.nomFr = nomFr;
    }

    public int getQuantite() {
        return quantite;
    }

    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public void setFournisseur(String fournisseur) {
        this.fournisseur = fournisseur;
    }

    public String getEmailFr() {
        return emailFr;
    }

    public void setEmailFr(String emailFr) {
        this.emailFr = emailFr;
    }

    public String getLocalisationFr() {
        return localisationFr;
    }

    public void setLocalisationFr(String localisationFr) {
        this.localisationFr = localisationFr;
    }

    public String getNumTelFr() {
        return numTelFr;
    }

    public void setNumTelFr(String numTelFr) {
        this.numTelFr = numTelFr;
    }

    @Override
    public String toString() {
        return "#" + idFr + " - " + (nomFr == null ? "" : nomFr);
    }
}

