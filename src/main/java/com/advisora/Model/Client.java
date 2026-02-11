package com.advisora.Model;

import com.advisora.enums.UserRole;

public class Client extends User {
    private double budget;
    private String description;

    // Default constructor
    public Client() {
        super();
        this.role = UserRole.CLIENT;
    }

    // Full constructor
    public Client(int id, String email, String password, String name, String firstName,
                  String phoneNumber, String dateN, double budget, String description) {
        super(id, email, password, name, firstName, phoneNumber, dateN, UserRole.CLIENT);
        this.budget = budget;
        this.description = description;
    }

    // Getters and Setters
    public double getBudget() {
        return budget;
    }

    public void setBudget(double budget) {
        this.budget = budget;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Client{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", firstName='" + firstName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", dateN='" + dateN + '\'' +
                ", budget=" + budget +
                ", description='" + description + '\'' +
                ", role=" + role +
                '}';
    }
}
