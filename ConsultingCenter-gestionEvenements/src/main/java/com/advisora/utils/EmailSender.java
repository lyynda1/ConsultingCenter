package com.advisora.utils;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

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
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(username));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(subject);
            msg.setText(body);
            Transport.send(msg);
        } catch (MessagingException e) {
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }
}