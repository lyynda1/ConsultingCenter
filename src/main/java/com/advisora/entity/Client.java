package com.advisora.entity;

import com.advisora.enums.UserRole;

import java.util.Objects;

public class Client extends User {

    private double budget;
    private String description;

    @Override
    public String toString() {
        return "Client{" +
                "budget=" + budget +
                ", description='" + description + '\'' +
                ", id=" + id +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", name='" + name + '\'' +
                ", firstName='" + firstName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", dateN='" + dateN + '\'' +
                ", role=" + role +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Client client = (Client) o;
        return Double.compare(budget, client.budget) == 0 && Objects.equals(description, client.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), budget, description);
    }

    public Client(int id, String email, String password, String name, String firstName,
                  String phoneNumber, String dateN, double budget, String description) {
        super(id, email, password, name, firstName, phoneNumber, dateN, UserRole.CLIENT);
        this.budget = budget;
        this.description = description;
    }

    public double getBudget() { return budget; }
    public String getDescription() { return description; }

    public void setBudget(double budget) {
        this.budget = budget;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
