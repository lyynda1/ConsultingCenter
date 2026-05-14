package com.advisora.Model.strategie;

import java.time.LocalDateTime;

public class Notification {
    private String title;
    private String message;
    private LocalDateTime timestamp;
    private boolean isRead;

    public Notification(String title, String message) {
        this.title = title;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.isRead = false;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public void setTimestamp(LocalDateTime dateNotification) {
        this.timestamp = dateNotification;
    }

}
