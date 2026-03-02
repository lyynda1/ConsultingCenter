package com.advisora.GUI.Game.api;

import javafx.scene.Node;

public interface PlayableGame {
    String id();
    String displayName();
    Node createView();
    void start();
    void reset();
    void stop();
    String statusText();
}

