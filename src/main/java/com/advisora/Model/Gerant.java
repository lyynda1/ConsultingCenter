package com.advisora.Model;

import com.advisora.enums.UserRole;

public class Gerant extends User {
    private String expertiseArea;

    // Default constructor
    public Gerant() {
        super();
        this.role = UserRole.MANAGER;
    }

    // Full constructor
    public Gerant(int id, String email, String password, String name, String firstName,
                  String phoneNumber, String dateN, String expertiseArea) {
        super(id, email, password, name, firstName, phoneNumber, dateN, UserRole.MANAGER);
        this.expertiseArea = expertiseArea;
    }

    // Getters and Setters
    public String getExpertiseArea() {
        return expertiseArea;
    }

    public void setExpertiseArea(String expertiseArea) {
        this.expertiseArea = expertiseArea;
    }

    @Override
    public String toString() {
        return "Gerant{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", firstName='" + firstName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", dateN='" + dateN + '\'' +
                ", expertiseArea='" + expertiseArea + '\'' +
                ", role=" + role +
                '}';
    }
}
