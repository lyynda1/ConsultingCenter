/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Domain model/entity used by business and UI layers
*/
package com.advisora.Model.strategie;

public class Objective {
    private int id;
    private String description;
    private int priority;
    private int strategieId;
    private String nomObjective;

    public Objective() {
    }

    public Objective(int strategieId, String nomObjective, String description, int priority) {
        this.strategieId = strategieId;
        this.nomObjective = nomObjective;
        this.description = description;
        this.priority = priority;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getStrategieId() {
        return strategieId;
    }

    public void setStrategieId(int strategieId) {
        this.strategieId = strategieId;
    }

    public String getNomObjective() {
        return nomObjective;
    }

    public void setNomObjective(String nomObjective) {
        this.nomObjective = nomObjective;
    }
}
