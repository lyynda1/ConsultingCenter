package com.advisora.accessibility;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class WindowsSapiSpeechEngine implements SpeechEngine {
    private final boolean available;
    private final ExecutorService executor;

    public WindowsSapiSpeechEngine() {
        this.available = detectAvailability();
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "voice-assistant-tts");
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void speakAsync(String text) {
        if (!available || text == null || text.isBlank()) return;
        executor.submit(() -> speakNow(text));
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }

    private static boolean detectAvailability() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (!os.contains("win")) return false;
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "$PSVersionTable.PSVersion.Major"
            );
            Process p = pb.start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void speakNow(String text) {
        String clean = escapeForPowerShell(text);
        String command = """
                Add-Type -AssemblyName System.Speech;
                $s = New-Object System.Speech.Synthesis.SpeechSynthesizer;
                try { $s.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::NotSet, [System.Speech.Synthesis.VoiceAge]::NotSet, 0, [System.Globalization.CultureInfo]::GetCultureInfo('fr-FR')) } catch {}
                $s.Rate = -1;
                $s.Speak('%s');
                $s.Dispose();
                """.formatted(clean);
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Drain output to prevent process blocking on full buffers.
            p.getInputStream().readAllBytes();
            p.waitFor();
        } catch (Exception e) {
            System.err.println("[VoiceAssistant] TTS error: " + e.getMessage());
        }
    }

    private static String escapeForPowerShell(String text) {
        String normalized = text
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .trim();
        String escaped = normalized.replace("'", "''");
        // Keep command compact to avoid shell parsing issues.
        if (escaped.length() > 400) {
            escaped = escaped.substring(0, 400);
        }
        return new String(escaped.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
