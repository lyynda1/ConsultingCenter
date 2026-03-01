package com.advisora.Model.projet;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class NewsArticle {
    private final String title;
    private final String description;
    private final String url;
    private final String source;
    private final String publishedAt;
    private final String category;
    private final int projectId;
    private final String projectTitle;

    public NewsArticle(
            String title,
            String description,
            String url,
            String source,
            String publishedAt,
            String category,
            int projectId,
            String projectTitle
    ) {
        this.title = safe(title);
        this.description = safe(description);
        this.url = safe(url);
        this.source = safe(source);
        this.publishedAt = safe(publishedAt);
        this.category = safe(category);
        this.projectId = projectId;
        this.projectTitle = safe(projectTitle);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getSource() {
        return source;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public String getCategory() {
        return category;
    }

    public int getProjectId() {
        return projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public String getDisplayTitle() {
        return title.isBlank() ? "Untitled article" : title;
    }

    public String getDisplaySource() {
        return source.isBlank() ? "Unknown source" : source;
    }

    public String getDisplayDescription() {
        return description.isBlank() ? "No description available." : description;
    }

    public String getDisplayPublishedAt() {
        if (publishedAt.isBlank()) {
            return "Unknown date";
        }
        try {
            return OffsetDateTime.parse(publishedAt).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeParseException ignored) {
            return publishedAt;
        }
    }

    public boolean hasValidUrl() {
        return !url.isBlank() && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

