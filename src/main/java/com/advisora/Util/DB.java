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
<<<<<<< HEAD
=======

    /**
     * Tests the database connection and displays connection details
     * @return true if connection is successful, false otherwise
     */
    public static boolean testConnection() {
        System.out.println("=== TESTING DATABASE CONNECTION ===");
        try (Connection conn = getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("✅ Database connection successful!");
                System.out.println("📊 Database: " + conn.getCatalog());
                System.out.println("🔗 URL: " + conn.getMetaData().getURL());
                System.out.println("👤 User: " + conn.getMetaData().getUserName());
                System.out.println();
                return true;
            }
        } catch (Exception e) {
            System.err.println("❌ Database connection failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return false;
    }

    /**
     * Tests connection and exits program if connection fails
     */
    public static void testConnectionOrExit() {
        if (!testConnection()) {
            System.err.println("\n⚠️ Exiting program due to database connection failure.");
            System.exit(1);
        }
    }
>>>>>>> gestionUtilisateurs
}
