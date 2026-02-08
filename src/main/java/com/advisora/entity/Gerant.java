package com.advisora.entity;

import com.advisora.enums.UserRole;

public class Gerant extends User {

    private String expertiseArea;

    public void setExpertiseArea(String expertiseArea) {
        this.expertiseArea = expertiseArea;
    }

    @Override
    public String toString() {
        return "Gerant{" +
                "expertiseArea='" + expertiseArea + '\'' +
                ", id=" + id +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", firstName='" + firstName + '\'' +
                ", role=" + role +
                ", dateN='" + dateN + '\'' +
                '}';
    }

    public Gerant(int id, String email, String password, String name, String firstName,
                  String phoneNumber, String dateN, String expertiseArea) {
        super(id, email, password, name, firstName, phoneNumber, dateN, UserRole.MANAGER);
        this.expertiseArea = expertiseArea;
    }

    public String getExpertiseArea() {
        return expertiseArea;
    }
}
