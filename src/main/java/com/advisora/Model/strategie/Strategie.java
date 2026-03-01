/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Domain model/entity used by business and UI layers
*/
package com.advisora.Model.strategie;

import com.advisora.Model.projet.Project;
import com.advisora.enums.StrategyStatut;
import com.advisora.enums.TypeStrategie;

import java.time.LocalDateTime;

public class Strategie {
    private int id;
    private int version;
    private String nomStrategie;

    private StrategyStatut statut;
    private LocalDateTime createdAt;
    private LocalDateTime lockedAt;
    private String news;
    private Project projet;
    private Integer idUser;
    private String justification;
    private TypeStrategie typeStrategie;
    private double budgetTotal;
    private double gainEstime;
    private String justifStrategie;
    private Integer dureeTerme;
    private boolean Approbation;

    public Strategie() {
        this.version = 1;
        this.statut = StrategyStatut.EN_COURS;
        this.justification = "";
        this.typeStrategie = TypeStrategie.NULL;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getNomStrategie() {
        return nomStrategie;
    }

    public void setNomStrategie(String nomStrategie) {
        this.nomStrategie = nomStrategie;
    }


    public StrategyStatut getStatut() {
        return statut;
    }

    public void setStatut(StrategyStatut statut) {
        this.statut = statut;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }



    public Integer getDureeTerme() { return dureeTerme; }
    public void setDureeTerme(Integer dureeTerme) { this.dureeTerme = dureeTerme; }

    public String getNews() {
        return news;
    }

    public void setNews(String news) {
        this.news = news;
    }

    public Project getProjet() {
        return projet;
    }

    public void setProjet(Project projet) {
        this.projet = projet;
    }

    public Integer getIdUser() {
        return idUser;
    }

    public void setIdUser(Integer idUser) {
        this.idUser = idUser;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }
    public String getJustification() {
        return justification;
    }

    public TypeStrategie getTypeStrategie() {
        return typeStrategie;
    }

    public void setTypeStrategie(TypeStrategie typeStrategie) {
        this.typeStrategie = typeStrategie;
    }

    public double getBudgetTotal() {
        return budgetTotal;
    }

    public void setBudgetTotal(double budgetTotal) {
        this.budgetTotal = budgetTotal;
    }

    public double getGainEstime() {
        return gainEstime;
    }

    public void setGainEstime(double gainEstime) {
        this.gainEstime = gainEstime;
    }

    @Override
    public String toString() {
        return "#" + id + " - " + (nomStrategie == null ? "" : nomStrategie);
    }

    public void setJustifStrategie(String trim) {
        this.justifStrategie = trim;
    }
    public String getJustifStrategie() {
        return justifStrategie;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Boolean getApprobation() {
        return this.Approbation;
    }
    public void setApprobation(Boolean approbation) {
        this.Approbation = approbation;
    }
}

