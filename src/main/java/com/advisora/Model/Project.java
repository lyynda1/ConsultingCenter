/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Domain model/entity used by business and UI layers
*/
package com.advisora.Model;

import com.advisora.enums.ProjectStatus;

import java.sql.Timestamp;

public class Project {
    private int idProj;
    private String titleProj;
    private String descriptionProj;
    private double budgetProj;
    private String typeProj;
    private ProjectStatus stateProj;
    private Timestamp createdAtProj;
    private Timestamp updatedAtProj;
    private double avancementProj;
    private int idClient;

    public Project() {
        this.stateProj = ProjectStatus.PENDING;
    }

    public Project(int idProj, String titleProj, String descriptionProj, double budgetProj, String typeProj, ProjectStatus stateProj, Timestamp createdAtProj, Timestamp updatedAtProj, double avancementProj, int idClient) {
        this.idProj = idProj;
        this.titleProj = titleProj;
        this.descriptionProj = descriptionProj;
        this.budgetProj = budgetProj;
        this.typeProj = typeProj;
        this.stateProj = stateProj;
        this.createdAtProj = createdAtProj;
        this.updatedAtProj = updatedAtProj;
        this.avancementProj = avancementProj;
        this.idClient = idClient;
    }

    public int getIdProj() { return idProj; }
    public void setIdProj(int idProj) { this.idProj = idProj; }
    public String getTitleProj() { return titleProj; }
    public void setTitleProj(String titleProj) { this.titleProj = titleProj; }
    public String getDescriptionProj() { return descriptionProj; }
    public void setDescriptionProj(String descriptionProj) { this.descriptionProj = descriptionProj; }
    public double getBudgetProj() { return budgetProj; }
    public void setBudgetProj(double budgetProj) { this.budgetProj = budgetProj; }
    public String getTypeProj() { return typeProj; }
    public void setTypeProj(String typeProj) { this.typeProj = typeProj; }
    public ProjectStatus getStateProj() { return stateProj; }
    public void setStateProj(ProjectStatus stateProj) { this.stateProj = stateProj; }
    public Timestamp getCreatedAtProj() { return createdAtProj; }
    public void setCreatedAtProj(Timestamp createdAtProj) { this.createdAtProj = createdAtProj; }
    public Timestamp getUpdatedAtProj() { return updatedAtProj; }
    public void setUpdatedAtProj(Timestamp updatedAtProj) { this.updatedAtProj = updatedAtProj; }
    public double getAvancementProj() { return avancementProj; }
    public void setAvancementProj(double avancementProj) { this.avancementProj = avancementProj; }
    public int getIdClient() { return idClient; }
    public void setIdClient(int idClient) { this.idClient = idClient; }

    @Override
    public String toString() {
        return "#" + idProj + " - " + titleProj;
    }
}
