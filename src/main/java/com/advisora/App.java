/*
ADVISORA STRUCTURE COMMENT
 param($m) 'File: ' + ($m.Groups[1].Value -replace '\\','/') 
Role: Application bootstrap/entrypoint
*/

package com.advisora;

import com.advisora.Services.TranslationService;
import com.advisora.Services.event.EventThresholdService;
import com.advisora.Services.event.EventReminderScheduler;
import com.advisora.Services.projet.TaskService;
import com.advisora.Services.strategie.RiskContext;
import com.advisora.Services.user.AdminAlertService;
import com.advisora.Services.user.UserService;
import com.advisora.utils.SceneThemeApplier;
import com.advisora.utils.i18n.I18n;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    private final EventReminderScheduler eventReminderScheduler = new EventReminderScheduler();

    @Override
    public void start(Stage primaryStage) {
        try {
            RiskContext.init();
            seedDefaultAdmin();
            seedDemoTasksForNotifications();
            scanAdminAlerts();
            scanEventThresholds();
            startEventReminders();

            initLocaleFromPrefs();
            System.out.println("Bundle test: " + com.advisora.utils.i18n.I18n.bundle().getString("nav.home"));// IMPORTANT

            Parent root = com.advisora.utils.i18n.FxLoader.load("/GUI/Auth/login.fxml");

            Scene scene = SceneThemeApplier.createScene(root);
            primaryStage.setTitle("Advisora - Login");
            SceneThemeApplier.setScene(primaryStage, scene);
            primaryStage.sceneProperty().addListener((obs, oldScene, newScene) -> SceneThemeApplier.apply(newScene));
            primaryStage.show();

        } catch (Throwable t) {
            // ✅ prints the REAL cause even if Maven hides it
            t.printStackTrace();
            System.exit(1);
        }
    }
    private void initLocaleFromPrefs() {
        try {
            java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.home"), ".advisora", "user_prefs.properties");
            if (!java.nio.file.Files.exists(p)) {
                I18n.setLocale(java.util.Locale.FRENCH);
                return;
            }
            java.util.Properties props = new java.util.Properties();
            try (var in = java.nio.file.Files.newInputStream(p)) {
                props.load(in);
            }
            String lang = props.getProperty("language", "FR").trim();
            I18n.setLocale("EN".equalsIgnoreCase(lang) ? java.util.Locale.ENGLISH : java.util.Locale.FRENCH);
        } catch (Exception e) {
            I18n.setLocale(java.util.Locale.FRENCH);
        }
    }
    @Override
    public void stop() {
        // âœ… stop scheduler thread on app close
        RiskContext.shutdown();
        eventReminderScheduler.stop();
    }

    private void seedDefaultAdmin() {
        String adminEmail = System.getenv("ADVISORA_ADMIN_EMAIL");
        if (adminEmail == null || adminEmail.isBlank()) adminEmail = "admin@advisora.local";

        String adminPassword = System.getenv("ADVISORA_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) adminPassword = "Admin123!";

        try {
            new UserService().ensureDefaultAdminAccount(adminEmail, adminPassword);
            System.out.println("[BOOT] admin account ensured for email: " + adminEmail);
        } catch (Exception e) {
            System.err.println("[BOOT] admin seed skipped: " + e.getMessage());
        }
    }

    private void scanAdminAlerts() {
        try {
            new AdminAlertService().scanInactiveManagers();
            System.out.println("[BOOT] admin alerts scan complete");
        } catch (Exception e) {
            System.err.println("[BOOT] admin alerts scan skipped: " + e.getMessage());
        }
    }

    private void seedDemoTasksForNotifications() {
        String enabled = System.getenv("ADVISORA_SEED_TASK_ALERTS");
        boolean run = enabled == null || enabled.isBlank() || "1".equals(enabled) || "true".equalsIgnoreCase(enabled);
        if (!run) return;

        try {
            int n = new TaskService().seedDemoTasksForNotifications();
            System.out.println("[BOOT] demo tasks for notifications inserted: " + n);
        } catch (Exception e) {
            System.err.println("[BOOT] demo task seed skipped: " + e.getMessage());
        }
    }

    private void scanEventThresholds() {
        try {
            int cancelled = new EventThresholdService().processDueThresholdCancellations();
            System.out.println("[BOOT] event threshold scan complete, cancelled events: " + cancelled);
        } catch (Exception e) {
            System.err.println("[BOOT] event threshold scan skipped: " + e.getMessage());
        }
    }

    private void startEventReminders() {
        try {
            eventReminderScheduler.start();
            System.out.println("[BOOT] event reminders scheduler started");
        } catch (Exception e) {
            System.err.println("[BOOT] event reminders scheduler skipped: " + e.getMessage());
        }
    }
}

