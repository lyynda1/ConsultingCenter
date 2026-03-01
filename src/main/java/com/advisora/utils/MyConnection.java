/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Infrastructure utility (connection/config helper)
*/
package com.advisora.utils;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class MyConnection {
    private static MyConnection instance;
    private final String url;
    private final String user;
    private final String password;

    private MyConnection() {
        try (InputStream in = MyConnection.class.getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new RuntimeException("db.properties introuvable");
            }
            Properties p = new Properties();
            p.load(in);
            url = p.getProperty("db.url");
            user = p.getProperty("db.user");
            password = p.getProperty("db.password");
        } catch (Exception e) {
            throw new RuntimeException("Erreur chargement configuration DB", e);
        }
    }

    public static synchronized MyConnection getInstance() {
        if (instance == null) {
            instance = new MyConnection();
        }
        return instance;
    }

    public synchronized Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}

