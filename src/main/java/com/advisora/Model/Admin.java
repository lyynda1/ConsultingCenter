package com.advisora.Model;

import com.advisora.enums.UserRole;

public class Admin extends User {

    // Default constructor
    public Admin() {
        super();
        this.role = UserRole.ADMIN;
    }

    // Full constructor
    public Admin(int id, String email, String password, String name, String firstName,
                 String phoneNumber, String dateN) {
        super(id, email, password, name, firstName, phoneNumber, dateN, UserRole.ADMIN);
    }

    @Override
    public String toString() {
        return "Admin{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", firstName='" + firstName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", dateN='" + dateN + '\'' +
                ", role=" + role +
                '}';
    }
}
