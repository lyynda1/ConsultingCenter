package com.advisora.Services.event;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class EventQrTicketService {
    private static final String DEFAULT_SECRET = "advisora-event-qr-secret";

    public QrTicket createTicket(int bookingId, int eventId, int userId) {
        String token = buildToken(bookingId, eventId, userId);
        String imagePath = generateQrPng(token, bookingId);
        return new QrTicket(token, imagePath);
    }

    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\|");
        if (parts.length < 6) {
            return false;
        }
        try {
            int bookingId = Integer.parseInt(parts[1]);
            int eventId = Integer.parseInt(parts[2]);
            int userId = Integer.parseInt(parts[3]);
            String issuedAt = parts[4];
            String sig = parts[5];

            String expected = sign(bookingId + "|" + eventId + "|" + userId + "|" + issuedAt);
            return expected.equalsIgnoreCase(sig);
        } catch (Exception ex) {
            return false;
        }
    }

    private String buildToken(int bookingId, int eventId, int userId) {
        String issuedAt = String.valueOf(System.currentTimeMillis());
        String payload = bookingId + "|" + eventId + "|" + userId + "|" + issuedAt;
        String sig = sign(payload);
        return "EVBK|" + bookingId + "|" + eventId + "|" + userId + "|" + issuedAt + "|" + sig;
    }

    private String generateQrPng(String token, int bookingId) {
        try {
            Path dir = Path.of("data", "qrcodes").toAbsolutePath();
            Files.createDirectories(dir);
            Path file = dir.resolve("booking-" + bookingId + "-" + LocalDateTime.now().toString().replace(':', '-') + ".png");

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 320, 320, hints);
            MatrixToImageWriter.writeToPath(matrix, "PNG", file);
            return file.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur generation QR ticket: " + ex.getMessage(), ex);
        }
    }

    private String sign(String payload) {
        try {
            String secret = System.getenv("ADVISORA_QR_SECRET");
            if (secret == null || secret.isBlank()) {
                secret = DEFAULT_SECRET;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload + "|" + secret).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 24);
        } catch (Exception ex) {
            throw new RuntimeException("Erreur signature QR: " + ex.getMessage(), ex);
        }
    }

    public static final class QrTicket {
        private final String token;
        private final String imagePath;

        public QrTicket(String token, String imagePath) {
            this.token = token;
            this.imagePath = imagePath;
        }

        public String getToken() {
            return token;
        }

        public String getImagePath() {
            return imagePath;
        }
    }
}

