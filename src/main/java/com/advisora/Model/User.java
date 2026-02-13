package com.advisora.Model;

import com.advisora.enums.UserRole;

public abstract class User {
    protected int id;
    protected String email;
    protected String password;
    protected String name;
    protected String firstName;
    protected String phoneNumber;
    protected String dateN;
    protected UserRole role;

    // Default constructor
    public User() {
    }

    // Constructor with all common fields
    public User(int id, String email, String password, String name, String firstName,
                String phoneNumber, String dateN, UserRole role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.firstName = firstName;
        this.phoneNumber = phoneNumber;
        this.dateN = dateN;
        this.role = role;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getDateN() {
        return dateN;
    }

    public void setDateN(String dateN) {
        this.dateN = dateN;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", firstName='" + firstName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", dateN='" + dateN + '\'' +
                ", role=" + role +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
