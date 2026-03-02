package com.advisora.Services.event;

import com.advisora.Model.event.EventBooking;
import com.advisora.Services.strategie.NotificationManager;
import com.advisora.enums.UserRole;
import com.advisora.utils.EmailSender;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class EventNotificationService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> DOTENV_CACHE = new HashMap<>();

    private final EventBookingService bookingService;

    public EventNotificationService() {
        this.bookingService = new EventBookingService();
    }

    public void sendBookingPendingPayment(EventBooking booking, String paymentUrl) {
        if (booking == null) {
            return;
        }
        String title = "Reservation creee - paiement en attente";
        String message = "Votre reservation pour '" + safe(booking.getEventTitle()) + "' est en attente de paiement.";
        addInAppNotification(title, message);

        Map<String, String> vars = baseVars(booking);
        vars.put("{{PAYMENT_URL}}", safe(paymentUrl));
        String body = renderTemplate("/templates/event/booking-pending-payment.txt", vars);
        sendEmailIfPossible(booking.getClientEmail(), title, body);
    }

    public void sendBookingConfirmed(EventBooking booking) {
        if (booking == null) {
            return;
        }
        String title = "Reservation confirmee";
        String message = "Votre reservation pour '" + safe(booking.getEventTitle()) + "' est confirmee.";
        addClientInAppNotification(title, message);

        String body = renderTemplate("/templates/event/booking-confirmed.txt", baseVars(booking));
        sendEmailWithQrIfPossible(booking.getClientEmail(), title, body, booking.getQrImagePathBk(), booking.getIdBk());
        bookingService.markNotificationSent(booking.getIdBk());
    }

    public void sendBookingRefunded(EventBooking booking, String reason) {
        if (booking == null) {
            return;
        }
        String title = "Reservation remboursee";
        String message = "Votre reservation pour '" + safe(booking.getEventTitle()) + "' a ete remboursee.";
        addClientInAppNotification(title, message);

        Map<String, String> vars = baseVars(booking);
        vars.put("{{REFUND_REASON}}", safe(reason));
        String body = renderTemplate("/templates/event/booking-refunded.txt", vars);
        sendEmailIfPossible(booking.getClientEmail(), title, body);
    }

    public void sendEventReminder(EventBooking booking, int hoursBefore) {
        if (booking == null) {
            return;
        }
        String title = "Rappel evenement (J-" + (hoursBefore / 24) + ")";
        String message = "Rappel: l'evenement '" + safe(booking.getEventTitle()) + "' commence bientot.";
        addClientInAppNotification(title, message);

        Map<String, String> vars = baseVars(booking);
        vars.put("{{HOURS_BEFORE}}", String.valueOf(hoursBefore));
        String body = renderTemplate("/templates/event/event-reminder.txt", vars);
        sendEmailIfPossible(booking.getClientEmail(), title, body);
    }

    private Map<String, String> baseVars(EventBooking booking) {
        Map<String, String> vars = new HashMap<>();
        vars.put("{{CLIENT_NAME}}", safe(booking.getClientName()));
        vars.put("{{EVENT_TITLE}}", safe(booking.getEventTitle()));
        vars.put("{{BOOKING_ID}}", String.valueOf(booking.getIdBk()));
        vars.put("{{BOOKING_DATE}}", booking.getBookingDate() == null ? "-" : booking.getBookingDate().format(DATE_FMT));
        vars.put("{{EVENT_START}}", booking.getEventStart() == null ? "-" : booking.getEventStart().format(DATE_FMT));
        vars.put("{{EVENT_END}}", booking.getEventEnd() == null ? "-" : booking.getEventEnd().format(DATE_FMT));
        vars.put("{{SEATS}}", String.valueOf(booking.getNumTicketBk()));
        vars.put("{{TOTAL_PRICE}}", String.valueOf(booking.getTotalPrixBk()));
        vars.put("{{CURRENCY}}", safe(booking.getEventCurrencyCode()));
        vars.put("{{PAYMENT_REF}}", safe(booking.getPaymentReference()));
        vars.put("{{QR_TOKEN}}", safe(booking.getQrTokenBk()));
        vars.put("{{QR_IMAGE_PATH}}", safe(booking.getQrImagePathBk()));
        return vars;
    }

    private String renderTemplate(String resourcePath, Map<String, String> vars) {
        String template = readResource(resourcePath);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            template = template.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return template;
    }

    private String readResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                return "Notification evenement";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "Notification evenement";
        }
    }

    private void addInAppNotification(String title, String message) {
        try {
            NotificationManager.getInstance().createIfNotExists(title, message, UserRole.CLIENT);
        } catch (Exception ex) {
            System.err.println("[EVENT-NOTIF] In-app notification skipped: " + ex.getMessage());
        }
    }

    private void addClientInAppNotification(String title, String message) {
        addInAppNotification(title, message);
    }

    private void sendEmailIfPossible(String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) {
            System.err.println("[EVENT-NOTIF] ✗ Email skipped: No recipient email provided");
            return;
        }
        EmailSender sender = buildSenderFromEnv();
        if (sender == null) {
            System.err.println("[EVENT-NOTIF] ✗ Email skipped: SMTP credentials not configured (check .env file)");
            return;
        }
        try {
            sender.send(recipient.trim(), subject, body);
        } catch (Exception ex) {
            System.err.println("[EVENT-NOTIF] ✗ Email failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void sendEmailWithQrIfPossible(String recipient, String subject, String body, String qrImagePath, int bookingId) {
        if (recipient == null || recipient.isBlank()) {
            System.err.println("[EVENT-NOTIF] ✗ Email+QR skipped: No recipient email provided");
            return;
        }
        EmailSender sender = buildSenderFromEnv();
        if (sender == null) {
            System.err.println("[EVENT-NOTIF] ✗ Email+QR skipped: SMTP credentials not configured (check .env file)");
            return;
        }
        try {
            sender.sendWithAttachment(
                    recipient.trim(),
                    subject,
                    body,
                    qrImagePath,
                    "ticket-booking-" + bookingId + ".png"
            );
        } catch (Exception ex) {
            System.err.println("[EVENT-NOTIF] ✗ Email+QR failed: " + ex.getMessage());
            ex.printStackTrace();
            System.out.println("[EVENT-NOTIF] Attempting to send without attachment...");
            sendEmailIfPossible(recipient, subject, body);
        }
    }

    private EmailSender buildSenderFromEnv() {
        String host = config("ADVISORA_SMTP_HOST", "smtp.gmail.com");
        String portText = config("ADVISORA_SMTP_PORT", "587");
        String user = config("ADVISORA_SMTP_USER", null);
        String password = config("ADVISORA_SMTP_PASSWORD", null);
        
        System.out.println("[EVENT-NOTIF] Building EmailSender - Host: " + host + " | Port: " + portText + " | User: " + (user != null ? user : "NOT SET"));
        
        if (user == null || password == null) {
            System.err.println("[EVENT-NOTIF] ✗ SMTP credentials missing! Set ADVISORA_SMTP_USER and ADVISORA_SMTP_PASSWORD in .env");
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (Exception ex) {
            port = 587;
        }
        return new EmailSender(host, port, user, password);
    }

    private String config(String key, String fallback) {
        String env = System.getenv(key);
        if (!isBlank(env)) {
            return env.trim();
        }
        ensureDotEnvLoaded();
        String fromFile = DOTENV_CACHE.get(key);
        String value = fromFile == null ? fallback : fromFile.trim();
        return value;
    }

    private void ensureDotEnvLoaded() {
        if (!DOTENV_CACHE.isEmpty()) {
            return;
        }
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            System.out.println("[EVENT-NOTIF] .env file not found at " + envPath.toAbsolutePath());
            return;
        }
        try {
            for (String raw : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                DOTENV_CACHE.putIfAbsent(k, v);
            }
            System.out.println("[EVENT-NOTIF] Loaded " + DOTENV_CACHE.size() + " env variables from .env file");
        } catch (Exception ex) {
            System.err.println("[EVENT-NOTIF] Error reading .env: " + ex.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isEmpty() || value.trim().isEmpty();
    }
    private String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value.trim();
    }
}