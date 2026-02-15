package com.advisora.Model;

import com.advisora.enums.RessourceStatut;

public class Ressource {
    private int idRs;
    private String nomRs;
    private double prixRs;
    private int quantiteRs;
    private RessourceStatut availabilityStatusRs;
    private int idFr;

    public int getIdRs() {
        return idRs;
    }

    public void setIdRs(int idRs) {
        this.idRs = idRs;
    }

    public String getNomRs() {
        return nomRs;
    }

    public void setNomRs(String nomRs) {
        this.nomRs = nomRs;
    }

    public double getPrixRs() {
        return prixRs;
    }

    public void setPrixRs(double prixRs) {
        this.prixRs = prixRs;
    }

    public int getQuantiteRs() {
        return quantiteRs;
    }

    public void setQuantiteRs(int quantiteRs) {
        this.quantiteRs = quantiteRs;
    }

    public RessourceStatut getAvailabilityStatusRs() {
        return availabilityStatusRs;
    }

    public void setAvailabilityStatusRs(RessourceStatut availabilityStatusRs) {
        this.availabilityStatusRs = availabilityStatusRs;
    }

    public int getIdFr() {
        return idFr;
    }

    public void setIdFr(int idFr) {
        this.idFr = idFr;
    }

    @Override
    public String toString() {
        return "#" + idRs + " - " + (nomRs == null ? "" : nomRs);
    }
}
