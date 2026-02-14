
package com.advisora.Model;
import com.advisora.enums.StrategyStatut;
import java.time.LocalDateTime;

public class Strategie {
    private int id;
    private String nomStrategie;
    private int version;
    private StrategyStatut statut;
    private LocalDateTime createdAt;
    private LocalDateTime lockedAt;

    private String news;
    private String objectif;
    private Project projet;

    // Default constructor
    public Strategie(int id, String nom, int version, StrategyStatut statut, LocalDateTime createdAt, LocalDateTime lockedAt, String news, String objectif) {
    }

    // Constructor with all fields
    public Strategie(int id, String nomStrategie, int version, StrategyStatut statut,
                     LocalDateTime createdAt, LocalDateTime lockedAt, String news, String objectif, Project projet) {
        this.id = id;
        this.nomStrategie = nomStrategie;
        this.version = version;
        this.statut = statut;
        this.createdAt = createdAt;
        this.lockedAt = lockedAt;
        this.news = news;
        this.objectif = objectif;
        this.projet = projet;
    }

    // Constructor without id (for new strategies)
    public Strategie(String nomStrategie, int version, StrategyStatut statut,
                     LocalDateTime createdAt, LocalDateTime lockedAt, String news, String objectif, Project projet) {
        this.nomStrategie = nomStrategie;
        this.version = version;
        this.statut = statut;
        this.createdAt = createdAt;
        this.lockedAt = lockedAt;
        this.news = news;
        this.objectif = objectif;
        this.projet = projet;
    }


    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNomStrategie() {
        return nomStrategie;
    }

    public void setNomStrategie(String nomStrategie) {
        this.nomStrategie = nomStrategie;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }



    public String getNews() {
        return news;
    }

    public void setNews(String news) {
        this.news = news;
    }

    public String getObjectif() {
        return objectif;
    }

    public void setObjectif(String objectif) {
        this.objectif = objectif;
    }
    public Project getProjet() {
        return projet;
    }

    public void setProjet(Project projet) {
        this.projet = projet;
    }

    // toString method for debugging
    @Override
    public String toString() {
        return "Strategie{" +
                "id=" + id +
                ", nom='" + nomStrategie + '\'' +
                ", version=" + version +
                ", statut=" + statut +
                ", createdAt=" + createdAt +
                ", lockedAt=" + lockedAt +
                ", news='" + news + '\'' +
                '}';
    }

    // equals and hashCode methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Strategie strategie = (Strategie) o;
        return id == strategie.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}