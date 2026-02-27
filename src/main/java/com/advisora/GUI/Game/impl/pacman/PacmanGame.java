package com.advisora.GUI.Game.impl.pacman;

import com.advisora.GUI.Game.api.PlayableGame;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class PacmanGame implements PlayableGame {
    private final PacmanLogic logic = new PacmanLogic();
    private final Canvas canvas = new Canvas(720, 480);
    private BorderPane root;
    private Timeline timeline;

    @Override
    public String id() {
        return "pacman";
    }

    @Override
    public String displayName() {
        return "Pac-Man";
    }

    @Override
    public Node createView() {
        if (root != null) {
            return root;
        }
        root = new BorderPane(canvas);
        root.getStyleClass().add("pacman-root");
        root.setBottom(buildMouseControls());
        root.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.UP) logic.setDesiredDirection(PacmanLogic.Direction.UP);
            if (e.getCode() == KeyCode.DOWN) logic.setDesiredDirection(PacmanLogic.Direction.DOWN);
            if (e.getCode() == KeyCode.LEFT) logic.setDesiredDirection(PacmanLogic.Direction.LEFT);
            if (e.getCode() == KeyCode.RIGHT) logic.setDesiredDirection(PacmanLogic.Direction.RIGHT);
            e.consume();
        });

        timeline = new Timeline(new KeyFrame(Duration.millis(180), e -> {
            logic.tick();
            render();
            if (logic.isGameOver()) {
                stop();
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);

        render();
        return root;
    }

    @Override
    public void start() {
        if (timeline != null) {
            timeline.playFromStart();
        }
        if (root != null) {
            root.requestFocus();
        }
    }

    @Override
    public void reset() {
        logic.reset();
        render();
        if (timeline != null) {
            timeline.playFromStart();
        }
        if (root != null) {
            root.requestFocus();
        }
    }

    @Override
    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
    }

    @Override
    public String statusText() {
        if (logic.isGameOver()) {
            return logic.isWin()
                    ? "Victoire - score " + logic.getScore()
                    : "Perdu - score " + logic.getScore();
        }
        return "Score " + logic.getScore() + " | Vies " + logic.getLives() + " | Points restants " + logic.getPelletsLeft();
    }

    private Node buildMouseControls() {
        Button up = dirButton("↑", PacmanLogic.Direction.UP);
        Button down = dirButton("↓", PacmanLogic.Direction.DOWN);
        Button left = dirButton("←", PacmanLogic.Direction.LEFT);
        Button right = dirButton("→", PacmanLogic.Direction.RIGHT);

        HBox middle = new HBox(8, left, down, right);
        middle.setAlignment(Pos.CENTER);

        VBox controls = new VBox(6, up, middle);
        controls.setAlignment(Pos.CENTER);
        controls.setStyle("-fx-padding: 10 0 0 0;");
        return controls;
    }

    private Button dirButton(String text, PacmanLogic.Direction direction) {
        Button button = new Button(text);
        button.setMinSize(48, 34);
        button.getStyleClass().add("game-dir-btn");
        button.setOnAction(e -> {
            logic.setDesiredDirection(direction);
            if (root != null) {
                root.requestFocus();
            }
        });
        return button;
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#111827"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        char[][] map = logic.snapshotMap();
        double cellW = canvas.getWidth() / map[0].length;
        double cellH = canvas.getHeight() / map.length;

        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[r].length; c++) {
                double x = c * cellW;
                double y = r * cellH;
                if (map[r][c] == '#') {
                    gc.setFill(Color.web("#1D4ED8"));
                    gc.fillRect(x, y, cellW, cellH);
                } else if (map[r][c] == '.') {
                    gc.setFill(Color.web("#FDE68A"));
                    gc.fillOval(x + cellW * 0.4, y + cellH * 0.4, cellW * 0.2, cellH * 0.2);
                }
            }
        }

        gc.setFill(Color.web("#FACC15"));
        gc.fillOval(logic.getPlayerCol() * cellW + cellW * 0.15,
                logic.getPlayerRow() * cellH + cellH * 0.15,
                cellW * 0.7,
                cellH * 0.7);

        gc.setFill(Color.web("#EF4444"));
        for (PacmanLogic.Ghost ghost : logic.getGhosts()) {
            gc.fillOval(ghost.col * cellW + cellW * 0.15,
                    ghost.row * cellH + cellH * 0.15,
                    cellW * 0.7,
                    cellH * 0.7);
        }
    }
}
