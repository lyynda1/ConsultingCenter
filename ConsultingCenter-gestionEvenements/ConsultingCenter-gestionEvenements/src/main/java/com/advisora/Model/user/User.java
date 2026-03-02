package com.advisora.Model.user;
import com.advisora.enums.UserRole;
import java.util.List;
import java.util.Objects;

public class User {
    int id;

    public String getCin() {
        return cin;
    }

    public void setCin(String cin) {
        this.cin = cin;
    }

    String cin;
    String email;
    String password;
    String nom;
    private String facePath;

    public String getFacePath() { return facePath; }
    public void setFacePath(String facePath) { this.facePath = facePath; }

    public boolean hasFace() {
        return facePath != null && !facePath.trim().isEmpty();
    }
    String prenom;
    String numTel;
    String dateN;
    UserRole role;
    String expertiseArea;
    private String imagePath;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id && cin== user.cin && Objects.equals(email, user.email) && Objects.equals(password, user.password) && Objects.equals(nom, user.nom) && Objects.equals(prenom, user.prenom) && Objects.equals(numTel, user.numTel) && Objects.equals(dateN, user.dateN) && role == user.role && Objects.equals(expertiseArea, user.expertiseArea);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cin, email, password, nom, prenom, numTel, dateN, role, expertiseArea);
    }
    public boolean hasImage() {
        return imagePath != null && !imagePath.trim().isEmpty();
    }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCIN() {
        return cin;
    }

    public void setCIN(String cin) {
        this.cin = cin;
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

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getNumTel() {
        return numTel;
    }

    public void setNumTel(String numTel) {
        this.numTel = numTel;
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

    public String getExpertiseArea() {
        return expertiseArea;
    }

    public List<userLog> getLogs() {
        return logs;
    }

    public void setLogs(List<userLog> logs) {
        this.logs = logs;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", CIN=" + cin +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", nom='" + nom + '\'' +
                ", prenom='" + prenom + '\'' +
                ", numTel='" + numTel + '\'' +
                ", dateN='" + dateN + '\'' +
                ", role=" + role +
                ", expertiseArea='" + expertiseArea + '\'' +
                '}';
    }

    public void setExpertiseArea(String expertiseArea) {
        this.expertiseArea = expertiseArea;
    }


    private List<userLog> logs;
}