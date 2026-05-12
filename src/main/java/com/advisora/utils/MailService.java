package com.advisora.utils;

public final class MailService {
    private MailService() {}

    public static void send(String to, String subject, String body) {
        MailConfig.requireSender().send(to, subject, body);
    }
}
