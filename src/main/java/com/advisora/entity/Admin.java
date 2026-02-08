package com.advisora.entity;

import com.advisora.enums.UserRole;

public class Admin extends User {
    @Override
    public String toString() {
        return "Admin{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", name='" + name + '\'' +
                ", firstName='" + firstName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", dateN='" + dateN + '\'' +
                ", role=" + role +
                '}';
    }

    public Admin(int id, String email, String password, String name, String firstName,
                 String phoneNumber, String dateN) {
        super(id, email, password, name, firstName, phoneNumber, dateN, UserRole.ADMIN);
    }
}
