package com.advisora.Model;

import java.sql.Time;
import java.util.Objects;

public class Investment {
    private int idInv;
    private String commentaireInv;
    private Time dureeInv;
    private double bud_minInv;
    private double bud_maxInv;
    private String currencyInv;
    private int idProj;
    private int idUser;

    // Constructors
    public Investment() {
        this.currencyInv = "TND"; // Default currency
    }

    public Investment(String commentaireInv, Time dureeInv, double bud_minInv,
                      double bud_maxInv, String currencyInv, int idProj, int idUser) {
        this.commentaireInv = commentaireInv;
        this.dureeInv = dureeInv;
        this.bud_minInv = bud_minInv;
        this.bud_maxInv = bud_maxInv;
        this.currencyInv = currencyInv;
        this.idProj = idProj;
        this.idUser = idUser;
    }

    public Investment(int idInv, String commentaireInv, Time dureeInv, double bud_minInv,
                      double bud_maxInv, String currencyInv, int idProj, int idUser) {
        this.idInv = idInv;
        this.commentaireInv = commentaireInv;
        this.dureeInv = dureeInv;
        this.bud_minInv = bud_minInv;
        this.bud_maxInv = bud_maxInv;
        this.currencyInv = currencyInv;
        this.idProj = idProj;
        this.idUser = idUser;
    }

    // Getters and Setters
    public int getIdInv() {
        return idInv;
    }

    public void setIdInv(int idInv) {
        this.idInv = idInv;
    }

    public String getCommentaireInv() {
        return commentaireInv;
    }

    public void setCommentaireInv(String commentaireInv) {
        this.commentaireInv = commentaireInv;
    }

    public Time getDureeInv() {
        return dureeInv;
    }

    public void setDureeInv(Time dureeInv) {
        this.dureeInv = dureeInv;
    }

    public double getBud_minInv() {
        return bud_minInv;
    }

    public void setBud_minInv(double bud_minInv) {
        this.bud_minInv = bud_minInv;
    }

    public double getBud_maxInv() {
        return bud_maxInv;
    }

    public void setBud_maxInv(double bud_maxInv) {
        this.bud_maxInv = bud_maxInv;
    }

    public String getCurrencyInv() {
        return currencyInv;
    }

    public void setCurrencyInv(String currencyInv) {
        this.currencyInv = currencyInv;
    }

    public int getIdProj() {
        return idProj;
    }

    public void setIdProj(int idProj) {
        this.idProj = idProj;
    }

    public int getIdUser() {
        return idUser;
    }

    public void setIdUser(int idUser) {
        this.idUser = idUser;
    }

    // toString method
    @Override
    public String toString() {
        return "Investment{" +
                "idInv=" + idInv +
                ", commentaireInv='" + commentaireInv + '\'' +
                ", dureeInv=" + dureeInv +
                ", bud_minInv=" + bud_minInv +
                ", bud_maxInv=" + bud_maxInv +
                ", currencyInv='" + currencyInv + '\'' +
                ", idProj=" + idProj +
                ", idUser=" + idUser +
                '}';
    }

    // equals and hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Investment that = (Investment) o;
        return idInv == that.idInv;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idInv);
    }
}