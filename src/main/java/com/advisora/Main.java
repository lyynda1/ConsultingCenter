package com.advisora;

import com.advisora.Util.DB;
import java.sql.Connection;

public class Main {
    public static void main(String[] args) {
        try (Connection cnx = DB.getConnection()) {
            System.out.println("✅ CONNECTED!");
            System.out.println("URL: " + cnx.getMetaData().getURL());
            System.out.println("User: " + cnx.getMetaData().getUserName());
        } catch (Exception e) {
            System.out.println("❌ CONNECTION FAILED!");
            e.printStackTrace();
        }
    }
}
