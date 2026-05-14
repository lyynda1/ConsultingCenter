package com.advisora.Model.strategie;

import com.advisora.enums.SWOTType;

import java.time.LocalDateTime;

public class SWOTItem {
    private int id;
    private int strategieId;
    private SWOTType type;
    private String description;
    private Integer weight; // nullable
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SWOTItem(int id, int strategieId, SWOTType type, String description, Integer weight, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.strategieId = strategieId;
        this.type = type;
        this.description = description;
        this.weight = weight;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public SWOTItem() {
    }

    public int getId() {
        return id;
    }

    public int getStrategieId() {
        return strategieId;
    }

    public SWOTType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Integer getWeight() {
        return weight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setStrategieId(int strategieId) {
        this.strategieId = strategieId;
    }

    public void setType(SWOTType type) {
        this.type = type;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    // getters/setters
}