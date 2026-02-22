package com.advisora.GUI.Game.impl.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class SnakeLogic {
    public enum Direction { UP, DOWN, LEFT, RIGHT }

    private final int rows;
    private final int cols;
    private final Random random;

    private final Deque<int[]> snake = new ArrayDeque<>();
    private Direction direction = Direction.RIGHT;
    private Direction nextDirection = Direction.RIGHT;
    private int[] food;
    private int score;
    private boolean gameOver;

    public SnakeLogic() {
        this(20, 24, new Random());
    }

    SnakeLogic(int rows, int cols, Random random) {
        this.rows = rows;
        this.cols = cols;
        this.random = random;
        reset();
    }

    public void reset() {
        snake.clear();
        snake.addFirst(new int[]{rows / 2, cols / 2});
        snake.addLast(new int[]{rows / 2, cols / 2 - 1});
        snake.addLast(new int[]{rows / 2, cols / 2 - 2});
        direction = Direction.RIGHT;
        nextDirection = Direction.RIGHT;
        score = 0;
        gameOver = false;
        spawnFood();
    }

    public void setDirection(Direction wanted) {
        if (wanted == null) return;
        if (direction == Direction.UP && wanted == Direction.DOWN) return;
        if (direction == Direction.DOWN && wanted == Direction.UP) return;
        if (direction == Direction.LEFT && wanted == Direction.RIGHT) return;
        if (direction == Direction.RIGHT && wanted == Direction.LEFT) return;
        nextDirection = wanted;
    }

    public void tick() {
        if (gameOver) return;
        direction = nextDirection;

        int[] head = snake.peekFirst();
        int nr = head[0];
        int nc = head[1];
        switch (direction) {
            case UP -> nr--;
            case DOWN -> nr++;
            case LEFT -> nc--;
            case RIGHT -> nc++;
        }

        if (nr < 0 || nc < 0 || nr >= rows || nc >= cols) {
            gameOver = true;
            return;
        }

        for (int[] part : snake) {
            if (part[0] == nr && part[1] == nc) {
                gameOver = true;
                return;
            }
        }

        snake.addFirst(new int[]{nr, nc});

        if (food[0] == nr && food[1] == nc) {
            score += 10;
            spawnFood();
        } else {
            snake.removeLast();
        }
    }

    public List<int[]> snakeParts() {
        return new ArrayList<>(snake);
    }

    public int[] food() {
        return new int[]{food[0], food[1]};
    }

    public int score() {
        return score;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    private void spawnFood() {
        while (true) {
            int r = random.nextInt(rows);
            int c = random.nextInt(cols);
            boolean hit = false;
            for (int[] part : snake) {
                if (part[0] == r && part[1] == c) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                food = new int[]{r, c};
                return;
            }
        }
    }
}
