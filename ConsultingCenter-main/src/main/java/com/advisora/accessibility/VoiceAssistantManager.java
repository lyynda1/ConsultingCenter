package com.advisora.accessibility;

import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class VoiceAssistantManager {
    private static final String PREF_NODE = "com.advisora.ui";
    private static final String PREF_KEY_ENABLED = "voiceAssistantEnabled";
    private static final String SCENE_INSTALLED = "voiceAssistant.installed";
    private static final long TOGGLE_COOLDOWN_MS = 600L;
    private static final long HOVER_DEBOUNCE_MS = 600L;
    private static final long REPEAT_GUARD_MS = 800L;
    private static final int MAX_SUMMARY_BUTTONS = 10;

    private static final Preferences PREFS = Preferences.userRoot().node(PREF_NODE);
    private static final SpeechEngine ENGINE = new WindowsSapiSpeechEngine();
    private static final Map<Scene, List<Consumer<Boolean>>> STATE_LISTENERS = new WeakHashMap<>();

    private static volatile boolean unavailableReported = false;
    private static volatile long lastToggleTs = 0L;
    private static volatile long lastHoverTs = 0L;
    private static volatile long lastSpeechTs = 0L;
    private static volatile String lastSpeechText = "";

    private VoiceAssistantManager() {
    }

    public static boolean isEnabled() {
        return PREFS.getBoolean(PREF_KEY_ENABLED, false);
    }

    public static VoiceAssistantMode getMode() {
        return isEnabled() ? VoiceAssistantMode.ON : VoiceAssistantMode.OFF;
    }

    public static void setEnabled(boolean enabled, Scene scene) {
        PREFS.putBoolean(PREF_KEY_ENABLED, enabled);
        if (!enabled) {
            speak("Assistant vocal desactive");
        } else {
            speak("Assistant vocal active");
            announcePrimaryButtons(scene);
        }
        notifyState(enabled);
    }

    public static boolean toggle(Scene scene) {
        boolean next = !isEnabled();
        setEnabled(next, scene);
        return next;
    }

    public static void applyToScene(Scene scene) {
        if (scene == null) return;
        if (Boolean.TRUE.equals(scene.getProperties().get(SCENE_INSTALLED))) return;
        scene.getProperties().put(SCENE_INSTALLED, Boolean.TRUE);

        installHotkey(scene);
        installFocusReader(scene);
        installHoverReader(scene);
    }

    public static void registerStateListener(Scene scene, Consumer<Boolean> listener) {
        if (scene == null || listener == null) return;
        synchronized (STATE_LISTENERS) {
            STATE_LISTENERS.computeIfAbsent(scene, s -> new ArrayList<>()).add(listener);
        }
        listener.accept(isEnabled());
    }

    private static void notifyState(boolean enabled) {
        synchronized (STATE_LISTENERS) {
            for (List<Consumer<Boolean>> listeners : STATE_LISTENERS.values()) {
                for (Consumer<Boolean> listener : listeners) {
                    Platform.runLater(() -> listener.accept(enabled));
                }
            }
        }
    }

    private static void installHotkey(Scene scene) {
        Set<KeyCode> pressed = ConcurrentHashMap.newKeySet();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            pressed.add(event.getCode());
            if (pressed.contains(KeyCode.F) && pressed.contains(KeyCode.J)) {
                long now = System.currentTimeMillis();
                if (now - lastToggleTs >= TOGGLE_COOLDOWN_MS) {
                    lastToggleTs = now;
                    boolean enabled = toggle(scene);
                    System.out.println("[VoiceAssistant] " + (enabled ? "enabled" : "disabled"));
                }
                event.consume();
            }
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> pressed.remove(event.getCode()));
    }

    private static void installFocusReader(Scene scene) {
        scene.focusOwnerProperty().addListener((obs, oldNode, newNode) -> {
            if (!isEnabled()) return;
            if (!(newNode instanceof Node node)) return;
            speakNode(node, false);
        });
    }

    private static void installHoverReader(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (!isEnabled()) return;
            long now = System.currentTimeMillis();
            if (now - lastHoverTs < HOVER_DEBOUNCE_MS) return;
            lastHoverTs = now;
            EventTarget target = event.getTarget();
            if (!(target instanceof Node node)) return;
            speakNode(node, true);
        });
    }

    private static void announcePrimaryButtons(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        List<String> labels = new ArrayList<>();
        for (Node node : scene.getRoot().lookupAll(".button")) {
            if (!(node instanceof Labeled labeled)) continue;
            if (!isReadable(node)) continue;
            String txt = normalizeText(labeled.getText());
            if (txt.isBlank()) continue;
            labels.add(txt);
            if (labels.size() >= MAX_SUMMARY_BUTTONS) break;
        }
        if (!labels.isEmpty()) {
            speak("Actions principales: " + String.join(", ", labels));
        }
    }

    private static void speakNode(Node rawNode, boolean fromHover) {
        Node node = resolveReadableNode(rawNode);
        if (node == null || !isReadable(node)) return;
        String text = describeNode(node);
        if (text.isBlank()) return;
        if (fromHover && text.equals(lastSpeechText)) return;
        speak(text);
    }

    private static Node resolveReadableNode(Node node) {
        Node cur = node;
        int depth = 0;
        while (cur != null && depth < 8) {
            if (cur instanceof Labeled
                    || cur instanceof TextInputControl
                    || cur instanceof ComboBoxBase<?>
                    || cur instanceof ListView<?>
                    || cur instanceof TableView<?>) {
                return cur;
            }
            cur = cur.getParent();
            depth++;
        }
        return node;
    }

    private static String describeNode(Node node) {
        if (node instanceof ButtonBase button) {
            return "Bouton " + normalizeText(button.getText());
        }
        if (node instanceof Labeled labeled) {
            String text = normalizeText(labeled.getText());
            if (!text.isBlank()) {
                return text;
            }
        }
        if (node instanceof TextInputControl input) {
            String prompt = normalizeText(input.getPromptText());
            String value = normalizeText(input.getText());
            if (!value.isBlank()) return "Champ " + value;
            if (!prompt.isBlank()) return "Champ " + prompt;
            return "Champ de texte";
        }
        if (node instanceof ComboBoxBase<?> combo) {
            Object value = combo.getValue();
            if (value != null) return "Selection " + value;
            return "Liste deroulante";
        }
        if (node instanceof ListView<?> listView) {
            Object item = listView.getSelectionModel() == null ? null : listView.getSelectionModel().getSelectedItem();
            return item == null ? "Liste" : "Liste, element selectionne " + item;
        }
        if (node instanceof TableView<?> tableView) {
            Object item = tableView.getSelectionModel() == null ? null : tableView.getSelectionModel().getSelectedItem();
            return item == null ? "Tableau" : "Tableau, ligne selectionnee " + item;
        }

        String accessible = normalizeText(node.getAccessibleText());
        if (!accessible.isBlank()) return accessible;

        if (node instanceof Control control) {
            Tooltip tooltip = control.getTooltip();
            if (tooltip != null) {
                String tip = normalizeText(tooltip.getText());
                if (!tip.isBlank()) return tip;
            }
        }

        return normalizeText(node.getId());
    }

    private static boolean isReadable(Node node) {
        return node != null && node.isVisible() && !node.isDisabled();
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static void speak(String text) {
        String msg = normalizeText(text);
        if (msg.isBlank()) return;
        long now = System.currentTimeMillis();
        if (Objects.equals(lastSpeechText, msg) && now - lastSpeechTs < REPEAT_GUARD_MS) return;
        lastSpeechText = msg;
        lastSpeechTs = now;

        if (!ENGINE.isAvailable()) {
            if (!unavailableReported) {
                unavailableReported = true;
                System.err.println("[VoiceAssistant] TTS unavailable on this machine.");
            }
            return;
        }
        ENGINE.speakAsync(msg);
    }
}
