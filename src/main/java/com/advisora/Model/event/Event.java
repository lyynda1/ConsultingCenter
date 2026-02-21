/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/')
Role: Domain model/entity used by business and UI layers
*/
package com.advisora.Model.event;

import java.time.LocalDateTime;

public class Event {
    private int idEv;
    private String titleEv;
    private String descriptionEv;
    private LocalDateTime startDateEv;
    private LocalDateTime endDateEv;
    private String organisateurName;
    private int capaciteEvnt;
    private String localisationEv;
    private Integer idGerant;

    public int getIdEv() {
        return idEv;
    }

    public void setIdEv(int idEv) {
        this.idEv = idEv;
    }

    public String getTitleEv() {
        return titleEv;
    }

    public void setTitleEv(String titleEv) {
        this.titleEv = titleEv;
    }

    public String getDescriptionEv() {
        return descriptionEv;
    }

    public void setDescriptionEv(String descriptionEv) {
        this.descriptionEv = descriptionEv;
    }

    public LocalDateTime getStartDateEv() {
        return startDateEv;
    }

    public void setStartDateEv(LocalDateTime startDateEv) {
        this.startDateEv = startDateEv;
    }

    public LocalDateTime getEndDateEv() {
        return endDateEv;
    }

    public void setEndDateEv(LocalDateTime endDateEv) {
        this.endDateEv = endDateEv;
    }

    public String getOrganisateurName() {
        return organisateurName;
    }

    public void setOrganisateurName(String organisateurName) {
        this.organisateurName = organisateurName;
    }

    public int getCapaciteEvnt() {
        return capaciteEvnt;
    }

    public void setCapaciteEvnt(int capaciteEvnt) {
        this.capaciteEvnt = capaciteEvnt;
    }

    public String getLocalisationEv() {
        return localisationEv;
    }

    public void setLocalisationEv(String localisationEv) {
        this.localisationEv = localisationEv;
    }

    public Integer getIdGerant() {
        return idGerant;
    }

    public void setIdGerant(Integer idGerant) {
        this.idGerant = idGerant;
    }
}
