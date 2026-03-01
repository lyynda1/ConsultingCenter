package com.advisora.Services.strategie;

import com.advisora.Model.strategie.ExternalEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JsonEventStore implements ExternalEventStore {

    private static final ObjectMapper OM = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

    private final Path filePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonEventStore(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public void upsert(ExternalEvent e) throws Exception {
        lock.writeLock().lock();
        try {
            List<ExternalEvent> all = readAllNoLock();

            String key = uniqueKey(e.getSource(), e.getEventType(), e.getEventName());

            // remove existing with same key
            all.removeIf(x -> uniqueKey(x.getSource(), x.getEventType(), x.getEventName()).equals(key));

            // update timestamps in a simple way
            if (e.getStartDate() == null && e.isActive()) {
                e.setStartDate(LocalDateTime.now());
            }
            if (!e.isActive()) {
                e.setEndDate(LocalDateTime.now());
            } else {
                e.setEndDate(null);
            }

            all.add(e);

            // keep newest first (optional)
            all.sort((a, b) -> {
                LocalDateTime da = a.getStartDate() == null ? LocalDateTime.MIN : a.getStartDate();
                LocalDateTime db = b.getStartDate() == null ? LocalDateTime.MIN : b.getStartDate();
                return db.compareTo(da);
            });

            writeAllNoLock(all);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ExternalEvent> getActiveEvents() throws Exception {
        lock.readLock().lock();
        try {
            List<ExternalEvent> all = readAllNoLock();
            List<ExternalEvent> active = new ArrayList<>();
            for (ExternalEvent e : all) {
                if (e.isActive()) active.add(e);
            }
            return active;
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<ExternalEvent> readAllNoLock() throws IOException {
        ensureFileExists();

        byte[] bytes = Files.readAllBytes(filePath);
        if (bytes.length == 0) return new ArrayList<>();

        try {
            return OM.readValue(bytes, new TypeReference<List<ExternalEvent>>() {});
        } catch (Exception ex) {
            // if file corrupted, don't crash app; start fresh
            return new ArrayList<>();
        }
    }

    private void writeAllNoLock(List<ExternalEvent> all) throws IOException {
        ensureFileExists();

        // atomic write: write to temp, then move
        Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        byte[] out = OM.writeValueAsBytes(all);

        Files.write(tmp, out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void ensureFileExists() throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(filePath)) {
            Files.write(filePath, "[]".getBytes(), StandardOpenOption.CREATE);
        }
    }

    private String uniqueKey(String source, String type, String name) {
        return (source == null ? "" : source) + "|" + (type == null ? "" : type) + "|" + (name == null ? "" : name);
    }
}
