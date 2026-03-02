package com.advisora.GUI.Game.impl.game2048;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Game2048Logic {
    private final int[][] board = new int[4][4];
    private int score;
    private final Random random;

    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    public Game2048Logic() {
        this(new Random());
    }

    Game2048Logic(Random random) {
        this.random = random;
    }

    public void reset() {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                board[r][c] = 0;
            }
        }
        score = 0;
        spawnRandomTile();
        spawnRandomTile();
    }

    public int getScore() {
        return score;
    }

    public int[][] snapshot() {
        int[][] out = new int[4][4];
        for (int r = 0; r < 4; r++) {
            System.arraycopy(board[r], 0, out[r], 0, 4);
        }
        return out;
    }

    public boolean move(Direction direction) {
        boolean moved = false;
        for (int i = 0; i < 4; i++) {
            int[] line = readLine(i, direction);
            int[] merged = mergeLine(line);
            moved |= writeLine(i, direction, merged);
        }
        if (moved) {
            spawnRandomTile();
        }
        return moved;
    }

    public boolean isGameOver() {
        if (!emptyCells().isEmpty()) {
            return false;
        }
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int v = board[r][c];
                if (r < 3 && board[r + 1][c] == v) return false;
                if (c < 3 && board[r][c + 1] == v) return false;
            }
        }
        return true;
    }

    public boolean hasWon() {
        for (int[] row : board) {
            for (int v : row) {
                if (v >= 2048) return true;
            }
        }
        return false;
    }

    private int[] readLine(int index, Direction direction) {
        int[] line = new int[4];
        for (int j = 0; j < 4; j++) {
            int r;
            int c;
            switch (direction) {
                case LEFT -> {
                    r = index;
                    c = j;
                }
                case RIGHT -> {
                    r = index;
                    c = 3 - j;
                }
                case UP -> {
                    r = j;
                    c = index;
                }
                default -> {
                    r = 3 - j;
                    c = index;
                }
            }
            line[j] = board[r][c];
        }
        return line;
    }

    private boolean writeLine(int index, Direction direction, int[] line) {
        boolean changed = false;
        for (int j = 0; j < 4; j++) {
            int r;
            int c;
            switch (direction) {
                case LEFT -> {
                    r = index;
                    c = j;
                }
                case RIGHT -> {
                    r = index;
                    c = 3 - j;
                }
                case UP -> {
                    r = j;
                    c = index;
                }
                default -> {
                    r = 3 - j;
                    c = index;
                }
            }
            if (board[r][c] != line[j]) {
                changed = true;
                board[r][c] = line[j];
            }
        }
        return changed;
    }

    private int[] mergeLine(int[] input) {
        List<Integer> values = new ArrayList<>();
        for (int v : input) {
            if (v != 0) values.add(v);
        }

        List<Integer> merged = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            int cur = values.get(i);
            if (i + 1 < values.size() && cur == values.get(i + 1)) {
                int sum = cur * 2;
                score += sum;
                merged.add(sum);
                i++;
            } else {
                merged.add(cur);
            }
        }

        int[] out = new int[4];
        for (int i = 0; i < merged.size(); i++) {
            out[i] = merged.get(i);
        }
        return out;
    }

    private void spawnRandomTile() {
        List<int[]> empty = emptyCells();
        if (empty.isEmpty()) return;
        int[] cell = empty.get(random.nextInt(empty.size()));
        board[cell[0]][cell[1]] = random.nextDouble() < 0.9 ? 2 : 4;
    }

    private List<int[]> emptyCells() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if (board[r][c] == 0) {
                    empty.add(new int[]{r, c});
                }
            }
        }
        return empty;
    }
}

