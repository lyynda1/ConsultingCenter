package com.advisora.utils;

public final class MailService {
    private static final EmailSender SENDER = new EmailSender(
            MailConfig.SMTP_HOST,
            MailConfig.SMTP_PORT,
            MailConfig.USERNAME,
            MailConfig.APP_PASSWORD
    );

    private MailService(){}

    public static void send(String to, String subject, String body) {
        SENDER.send(to, subject, body);
    }
}