package com.advisora.Model.projet;

import com.advisora.enums.TaskStatus;

import java.sql.Timestamp;

public class Task {
    private int id;
    private int projectId;
    private String title;
    private TaskStatus status;
    private int weight;
    private int durationDays;
    private java.sql.Date lastWarningDate;
    private Timestamp createdAt;

    public Task() {
        this.status = TaskStatus.TODO;
        this.weight = 1;
        this.durationDays = 1;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(int durationDays) {
        this.durationDays = durationDays;
    }

    public java.sql.Date getLastWarningDate() {
        return lastWarningDate;
    }

    public void setLastWarningDate(java.sql.Date lastWarningDate) {
        this.lastWarningDate = lastWarningDate;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
