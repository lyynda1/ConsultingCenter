package com.advisora.utils.news;



import com.advisora.Model.strategie.NewsItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class NewsJsonStore {

    private static final ObjectMapper OM = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path path;

    public NewsJsonStore(Path path) {
        this.path = path;
    }

    public List<NewsItem> readAll() {
        try {
            ensureFile();
            String json = Files.readString(path);
            if (json == null || json.trim().isEmpty()) return new ArrayList<>();
            return OM.readValue(json, new TypeReference<List<NewsItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void writeAll(List<NewsItem> items) {
        try {
            ensureFile();
            Files.writeString(path, OM.writeValueAsString(items),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    private void ensureFile() throws Exception {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
        if (!Files.exists(path)) Files.writeString(path, "[]", StandardOpenOption.CREATE);
    }
}