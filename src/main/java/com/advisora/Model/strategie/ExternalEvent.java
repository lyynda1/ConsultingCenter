package com.advisora.Model.strategie;

import com.advisora.enums.Severity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ExternalEvent {

    // No idEvent needed for JSON storage (prevents the null int crash)
    private String source;        // WHO / GDELT / RSS / TEST
    private String eventType;     // ECONOMY / HEALTH / REGULATION / LOGISTICS / SECURITY
    private String eventName;     // Unique stable name for upsert

    private Double currentValue;  // e.g. count of articles
    private String unit;          // e.g. "articles/48h"

    private Severity severity = Severity.INFO;
    private String description;
    public List<String> suggestions = new ArrayList<>();

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active = true;

    public ExternalEvent() {}

    // ---- Getters / Setters ----

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public Double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Double currentValue) {
        this.currentValue = currentValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
