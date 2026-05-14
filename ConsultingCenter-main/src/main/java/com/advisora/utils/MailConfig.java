package com.advisora.utils;

public final class MailConfig {
    private MailConfig() {}

    public static final String SMTP_HOST = "smtp.gmail.com";
    public static final int SMTP_PORT = 587;
    public static final String USERNAME = "lyynda19@gmail.com";
    public static final String APP_PASSWORD = "vfpj vcuf lojq bqzd".replace(" ", "");

    public static String smtpHost() {
        return config("ADVISORA_SMTP_HOST", SMTP_HOST);
    }

    public static int smtpPort() {
        String portText = config("ADVISORA_SMTP_PORT", String.valueOf(SMTP_PORT));
        try {
            return Integer.parseInt(portText);
        } catch (Exception ignored) {
            return SMTP_PORT;
        }
    }

    public static String username() {
        return config("ADVISORA_SMTP_USER", USERNAME);
    }

    public static String password() {
        return config("ADVISORA_SMTP_PASSWORD", APP_PASSWORD);
    }

    public static boolean hasCredentials() {
        String user = username();
        String password = password();
        return user != null && !user.isBlank() && password != null && !password.isBlank();
    }

    public static EmailSender createSenderOrNull() {
        if (!hasCredentials()) {
            return null;
        }
        return new EmailSender(smtpHost(), smtpPort(), username(), password());
    }

    public static EmailSender requireSender() {
        EmailSender sender = createSenderOrNull();
        if (sender == null) {
            throw new RuntimeException("SMTP credentials missing. Set ADVISORA_SMTP_USER and ADVISORA_SMTP_PASSWORD or update MailConfig.");
        }
        return sender;
    }

    private static String config(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        return fallback;
    }
}
