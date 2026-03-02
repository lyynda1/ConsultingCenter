package com.advisora.GUI.Game.impl.snake;

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

import java.util.List;

public class SnakeGame implements PlayableGame {
    private final SnakeLogic logic = new SnakeLogic();
    private final Canvas canvas = new Canvas(720, 500);
    private BorderPane root;
    private Timeline timeline;

    @Override
    public String id() {
        return "snake";
    }

    @Override
    public String displayName() {
        return "Snake";
    }

    @Override
    public Node createView() {
        if (root != null) return root;

        root = new BorderPane(canvas);
        root.getStyleClass().add("snake-root");
        root.setBottom(buildMouseControls());
        root.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.UP) logic.setDirection(SnakeLogic.Direction.UP);
            if (e.getCode() == KeyCode.DOWN) logic.setDirection(SnakeLogic.Direction.DOWN);
            if (e.getCode() == KeyCode.LEFT) logic.setDirection(SnakeLogic.Direction.LEFT);
            if (e.getCode() == KeyCode.RIGHT) logic.setDirection(SnakeLogic.Direction.RIGHT);
            e.consume();
        });

        timeline = new Timeline(new KeyFrame(Duration.millis(130), e -> {
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
        if (timeline != null) timeline.playFromStart();
        if (root != null) root.requestFocus();
    }

    @Override
    public void reset() {
        logic.reset();
        render();
        if (timeline != null) timeline.playFromStart();
        if (root != null) root.requestFocus();
    }

    @Override
    public void stop() {
        if (timeline != null) timeline.stop();
    }

    @Override
    public String statusText() {
        if (logic.isGameOver()) {
            return "Perdu - score " + logic.score();
        }
        return "Score " + logic.score();
    }

    private Node buildMouseControls() {
        Button up = dirButton("\u2191", SnakeLogic.Direction.UP);
        Button down = dirButton("\u2193", SnakeLogic.Direction.DOWN);
        Button left = dirButton("\u2190", SnakeLogic.Direction.LEFT);
        Button right = dirButton("\u2192", SnakeLogic.Direction.RIGHT);

        HBox middle = new HBox(8, left, down, right);
        middle.setAlignment(Pos.CENTER);

        VBox controls = new VBox(6, up, middle);
        controls.setAlignment(Pos.CENTER);
        controls.setStyle("-fx-padding: 10 0 0 0;");
        return controls;
    }

    private Button dirButton(String text, SnakeLogic.Direction direction) {
        Button button = new Button(text);
        button.getStyleClass().add("game-dir-btn");
        button.setMinSize(48, 34);
        button.setOnAction(e -> {
            logic.setDirection(direction);
            if (root != null) root.requestFocus();
        });
        return button;
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double cw = canvas.getWidth() / logic.cols();
        double ch = canvas.getHeight() / logic.rows();

        gc.setFill(Color.web("#f97316"));
        int[] food = logic.food();
        gc.fillRoundRect(food[1] * cw + 2, food[0] * ch + 2, cw - 4, ch - 4, 6, 6);

        List<int[]> parts = logic.snakeParts();
        for (int i = 0; i < parts.size(); i++) {
            int[] p = parts.get(i);
            gc.setFill(i == 0 ? Color.web("#22c55e") : Color.web("#16a34a"));
            gc.fillRoundRect(p[1] * cw + 2, p[0] * ch + 2, cw - 4, ch - 4, 6, 6);
        }
    }
}

