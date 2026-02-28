package com.advisora.GUI.Game.impl.game2048;

import com.advisora.GUI.Game.api.PlayableGame;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class Game2048 implements PlayableGame {
    private final Game2048Logic logic = new Game2048Logic();
    private final Label scoreLabel = new Label();
    private final Label stateLabel = new Label();
    private final Label[][] cellLabels = new Label[4][4];
    private BorderPane root;

    @Override
    public String id() {
        return "g2048";
    }

    @Override
    public String displayName() {
        return "2048";
    }

    @Override
    public Node createView() {
        if (root != null) {
            return root;
        }

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);

        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                Label label = new Label();
                label.setMinSize(70, 70);
                label.setAlignment(Pos.CENTER);
                label.getStyleClass().add("g2048-cell");
                cellLabels[r][c] = label;
                StackPane tile = new StackPane(label);
                tile.getStyleClass().add("g2048-tile");
                grid.add(tile, c, r);
            }
        }

        scoreLabel.getStyleClass().add("game-status");
        stateLabel.getStyleClass().add("game-status");

        root = new BorderPane();
        root.getStyleClass().add("g2048-root");
        BorderPane top = new BorderPane();
        top.setLeft(scoreLabel);
        top.setRight(stateLabel);
        root.setTop(top);
        root.setCenter(grid);
        root.setBottom(buildMouseControls());

        root.setOnKeyPressed(e -> {
            boolean moved = switch (e.getCode()) {
                case UP -> logic.move(Game2048Logic.Direction.UP);
                case DOWN -> logic.move(Game2048Logic.Direction.DOWN);
                case LEFT -> logic.move(Game2048Logic.Direction.LEFT);
                case RIGHT -> logic.move(Game2048Logic.Direction.RIGHT);
                default -> false;
            };
            if (moved) {
                refresh();
            }
            e.consume();
        });
        return root;
    }

    @Override
    public void start() {
        if (logic.getScore() == 0 && isBoardEmpty()) {
            logic.reset();
            refresh();
        }
        if (root != null) {
            root.requestFocus();
        }
    }

    @Override
    public void reset() {
        logic.reset();
        refresh();
        if (root != null) {
            root.requestFocus();
        }
    }

    @Override
    public void stop() {
        // No background loops in this game.
    }

    @Override
    public String statusText() {
        if (logic.isGameOver()) {
            return "Game over - score " + logic.getScore();
        }
        if (logic.hasWon()) {
            return "Vous avez atteint 2048!";
        }
        return "Score " + logic.getScore();
    }

    private Node buildMouseControls() {
        Button up = dirButton("↑", Game2048Logic.Direction.UP);
        Button down = dirButton("↓", Game2048Logic.Direction.DOWN);
        Button left = dirButton("←", Game2048Logic.Direction.LEFT);
        Button right = dirButton("→", Game2048Logic.Direction.RIGHT);

        HBox middle = new HBox(8, left, down, right);
        middle.setAlignment(Pos.CENTER);

        VBox controls = new VBox(6, up, middle);
        controls.setAlignment(Pos.CENTER);
        controls.setStyle("-fx-padding: 10 0 0 0;");
        return controls;
    }

    private Button dirButton(String text, Game2048Logic.Direction direction) {
        Button button = new Button(text);
        button.setMinSize(48, 34);
        button.getStyleClass().add("game-dir-btn");
        button.setOnAction(e -> {
            if (logic.move(direction)) {
                refresh();
            }
            if (root != null) {
                root.requestFocus();
            }
        });
        return button;
    }

    private boolean isBoardEmpty() {
        int[][] s = logic.snapshot();
        for (int[] row : s) {
            for (int v : row) {
                if (v != 0) return false;
            }
        }
        return true;
    }

    private void refresh() {
        int[][] board = logic.snapshot();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int v = board[r][c];
                Label cell = cellLabels[r][c];
                cell.setText(v == 0 ? "" : String.valueOf(v));
                cell.getStyleClass().removeIf(s -> s.startsWith("g2048-v"));
                if (v != 0) {
                    cell.getStyleClass().add("g2048-v" + v);
                }
            }
        }
        scoreLabel.setText("Score: " + logic.getScore());
        if (logic.isGameOver()) {
            stateLabel.setText("Perdu");
        } else if (logic.hasWon()) {
            stateLabel.setText("Gagne");
        } else {
            stateLabel.setText("");
        }
    }
}
