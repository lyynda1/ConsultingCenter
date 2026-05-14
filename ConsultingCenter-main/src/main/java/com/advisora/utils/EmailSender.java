package com.advisora.utils;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

public class EmailSender {

    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password; // Gmail App Password (recommended)

    public EmailSender(String smtpHost, int smtpPort, String username, String password) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
    }

    public void send(String to, String subject, String body) {
        Session session = createSession();

        try {
            System.out.println("[EMAIL] Sending to: " + to + " | Subject: " + subject);
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(username));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(subject);
            
            // Auto-detect HTML content
            if (body != null && body.trim().startsWith("<!DOCTYPE") || (body != null && body.contains("<html"))) {
                msg.setContent(body, "text/html; charset=utf-8");
            } else {
                msg.setText(body);
            }
            
            Transport.send(msg);
            System.out.println("[EMAIL] âœ“ Email sent successfully to: " + to);
        } catch (MessagingException e) {
            System.err.println("[EMAIL] âœ— Failed to send email: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    public void sendWithAttachment(String to, String subject, String body, String attachmentPath, String attachmentName) {
        if (attachmentPath == null || attachmentPath.isBlank()) {
            send(to, subject, body);
            return;
        }

        File file = new File(attachmentPath);
        if (!file.exists() || !file.isFile()) {
            System.err.println("[EMAIL] Attachment not found: " + attachmentPath + " - Sending without attachment");
            send(to, subject, body);
            return;
        }

        Session session = createSession();

        try {
            System.out.println("[EMAIL] Sending with attachment to: " + to + " | Subject: " + subject + " | File: " + attachmentName);
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(username));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(subject);

            // Create body part with HTML support
            MimeBodyPart textPart = new MimeBodyPart();
            if (body != null && (body.trim().startsWith("<!DOCTYPE") || body.contains("<html"))) {
                textPart.setContent(body, "text/html; charset=utf-8");
            } else {
                textPart.setText(body);
            }

            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(file);
            if (attachmentName != null && !attachmentName.isBlank()) {
                attachmentPart.setFileName(attachmentName.trim());
            }

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachmentPart);

            msg.setContent(multipart);
            Transport.send(msg);
            System.out.println("[EMAIL] âœ“ Email with attachment sent successfully to: " + to);
        } catch (MessagingException | IOException e) {
            System.err.println("[EMAIL] âœ— Failed to send email with attachment: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Email send with attachment failed: " + e.getMessage(), e);
        }
    }

    private Session createSession() {
        System.out.println("[EMAIL] SMTP Config - Host: " + smtpHost + " | Port: " + smtpPort + " | User: " + username);
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.ssl.trust", smtpHost);

        return Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}
