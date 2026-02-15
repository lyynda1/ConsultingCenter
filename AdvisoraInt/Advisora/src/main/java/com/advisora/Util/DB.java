package com.advisora.Util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public final class DB {
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        try (InputStream in = DB.class.getResourceAsStream("/db.properties")) {
            if (in == null) throw new RuntimeException("db.properties not found in src/main/resources");
            Properties p = new Properties();
            p.load(in);

            URL = p.getProperty("db.url");
            USER = p.getProperty("db.user");
            PASSWORD = p.getProperty("db.password");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load db.properties", e);
        }
    }

    private DB() {}

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (Exception e) {
            throw new RuntimeException("DB connection failed: " + e.getMessage(), e);
        }
    }
}
