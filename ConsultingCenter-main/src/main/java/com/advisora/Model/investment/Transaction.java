/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Domain model/entity used by business and UI layers
*/
package com.advisora.Model.investment;

import com.advisora.enums.transactionStatut;

import java.sql.Date;
import java.util.Objects;

public class Transaction {
    private int idTransac;
    private Date dateTransac;
    private double montantTransac;
    private String type;
    private transactionStatut statut;
    private int idInv;

    public Transaction() {
        this.statut = transactionStatut.PENDING;
    }

    public Transaction(Date dateTransac, double montantTransac, String type,
                       transactionStatut statut, int idInv) {
        this.dateTransac = dateTransac;
        this.montantTransac = montantTransac;
        this.type = type;
        this.statut = statut;
        this.idInv = idInv;
    }

    public Transaction(int idTransac, Date dateTransac, double montantTransac,
                       String type, transactionStatut statut, int idInv) {
        this.idTransac = idTransac;
        this.dateTransac = dateTransac;
        this.montantTransac = montantTransac;
        this.type = type;
        this.statut = statut;
        this.idInv = idInv;
    }

    public int getIdTransac() { return idTransac; }
    public void setIdTransac(int idTransac) { this.idTransac = idTransac; }
    public Date getDateTransac() { return dateTransac; }
    public void setDateTransac(Date dateTransac) { this.dateTransac = dateTransac; }
    public double getMontantTransac() { return montantTransac; }
    public void setMontantTransac(double montantTransac) { this.montantTransac = montantTransac; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public transactionStatut getStatut() { return statut; }
    public void setStatut(transactionStatut statut) { this.statut = statut; }
    public int getIdInv() { return idInv; }
    public void setIdInv(int idInv) { this.idInv = idInv; }

    @Override
    public String toString() {
        return "Transaction{" +
                "idTransac=" + idTransac +
                ", dateTransac=" + dateTransac +
                ", montantTransac=" + montantTransac +
                ", type='" + type + '\'' +
                ", statut=" + statut +
                ", idInv=" + idInv +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return idTransac == that.idTransac;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idTransac);
    }
}

