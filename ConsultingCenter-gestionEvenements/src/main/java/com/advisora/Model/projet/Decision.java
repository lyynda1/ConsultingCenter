/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Domain model/entity used by business and UI layers
*/
package com.advisora.Model.projet;

import com.advisora.enums.DecisionStatus;

import java.time.LocalDateTime;

public class Decision {
    private int idD;
    private DecisionStatus statutD;
    private String descriptionD;
    private LocalDateTime dateDecision;
    private int idProj;
    private int idUser;

    public Decision() {
        this.statutD = DecisionStatus.PENDING;
        this.dateDecision = LocalDateTime.now();
    }

    public Decision(int idD, DecisionStatus statutD, String descriptionD, LocalDateTime dateDecision, int idProj, int idUser) {
        this.idD = idD;
        this.statutD = statutD;
        this.descriptionD = descriptionD;
        this.dateDecision = dateDecision;
        this.idProj = idProj;
        this.idUser = idUser;
    }

    public int getIdD() { return idD; }
    public void setIdD(int idD) { this.idD = idD; }
    public DecisionStatus getStatutD() { return statutD; }
    public void setStatutD(DecisionStatus statutD) { this.statutD = statutD; }
    public String getDescriptionD() { return descriptionD; }
    public void setDescriptionD(String descriptionD) { this.descriptionD = descriptionD; }
    public LocalDateTime getDateDecision() { return dateDecision; }
    public void setDateDecision(LocalDateTime dateDecision) { this.dateDecision = dateDecision; }
    public int getIdProj() { return idProj; }
    public void setIdProj(int idProj) { this.idProj = idProj; }
    public int getIdUser() { return idUser; }
    public void setIdUser(int idUser) { this.idUser = idUser; }

    @Override
    public String toString() {
        return "#" + idD + " - " + statutD;
    }
}
