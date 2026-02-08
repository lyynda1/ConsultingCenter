package com.advisora.entity;

import com.advisora.enums.UserRole;
import java.util.Objects;

public class User {

    protected int id;
    protected String email;

    public void setEmail(String email) {
        this.email = email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setDateN(String dateN) {
        this.dateN = dateN;
    }

    protected String password;
    protected String name;
    protected String firstName;
    protected String phoneNumber;
    protected String dateN;
    protected UserRole role;

    public User() {}

    public User(int id, String email, String password, String name, String firstName,
                String phoneNumber, String dateN, UserRole role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.firstName = firstName; // ✅ FIXED
        this.phoneNumber = phoneNumber;
        this.dateN = dateN;
        this.role = role;
    }

    // getters & setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public String getFirstName() { return firstName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getDateN() { return dateN; }
    public UserRole getRole() { return role; }

    public void setRole(UserRole role) { this.role = role; }

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
        if (!(o instanceof User user)) return false;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
