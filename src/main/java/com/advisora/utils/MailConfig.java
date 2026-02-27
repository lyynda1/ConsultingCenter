package com.advisora.utils;

public final class MailConfig {
    private MailConfig(){}

    // ✅ Mets ton gmail ici (le sender)
    public static final String SMTP_HOST = "smtp.gmail.com";
    public static final int SMTP_PORT = 587;

    public static final String USERNAME = "lyynda19@gmail.com";

    // ✅ ICI tu mets App Password (16 chars) pas ton mdp gmail normal
    public static final String APP_PASSWORD = "bsiy vjdy yaep ikom".replace(" ", "");
}