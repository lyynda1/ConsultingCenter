package com.advisora.GUI.Game.impl.pacman;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PacmanLogic {
    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    public static class Ghost {
        int row;
        int col;

        Ghost(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    private final char[][] map;
    private int playerRow;
    private int playerCol;
    private Direction desiredDirection = Direction.LEFT;
    private int score;
    private int lives = 3;
    private int pelletsLeft;
    private final List<Ghost> ghosts = new ArrayList<>();
    private final Random random;

    public PacmanLogic() {
        this(new Random());
    }

    PacmanLogic(Random random) {
        this.random = random;
        this.map = buildMap();
        reset();
    }

    public void reset() {
        ghosts.clear();
        char[][] start = buildMap();
        for (int r = 0; r < map.length; r++) {
            System.arraycopy(start[r], 0, map[r], 0, map[r].length);
        }

        playerRow = 1;
        playerCol = 1;
        desiredDirection = Direction.RIGHT;
        score = 0;
        lives = 3;

        ghosts.add(new Ghost(9, 9));
        ghosts.add(new Ghost(9, 10));

        pelletsLeft = 0;
        for (char[] row : map) {
            for (char cell : row) {
                if (cell == '.') pelletsLeft++;
            }
        }
    }

    public void setDesiredDirection(Direction desiredDirection) {
        this.desiredDirection = desiredDirection;
    }

    public void tick() {
        if (isGameOver()) return;

        movePlayer();
        eatPellet();
        moveGhosts();
        checkCollision();
    }

    public boolean isGameOver() {
        return lives <= 0 || pelletsLeft <= 0;
    }

    public boolean isWin() {
        return pelletsLeft <= 0;
    }

    public int getScore() {
        return score;
    }

    public int getLives() {
        return lives;
    }

    public int getPelletsLeft() {
        return pelletsLeft;
    }

    public int getPlayerRow() {
        return playerRow;
    }

    public int getPlayerCol() {
        return playerCol;
    }

    public List<Ghost> getGhosts() {
        return ghosts;
    }

    public char[][] snapshotMap() {
        char[][] out = new char[map.length][map[0].length];
        for (int r = 0; r < map.length; r++) {
            System.arraycopy(map[r], 0, out[r], 0, map[r].length);
        }
        return out;
    }

    private void movePlayer() {
        int nr = playerRow;
        int nc = playerCol;
        switch (desiredDirection) {
            case UP -> nr--;
            case DOWN -> nr++;
            case LEFT -> nc--;
            case RIGHT -> nc++;
        }
        if (isWalkable(nr, nc)) {
            playerRow = nr;
            playerCol = nc;
        }
    }

    private void eatPellet() {
        if (map[playerRow][playerCol] == '.') {
            map[playerRow][playerCol] = ' ';
            pelletsLeft--;
            score += 10;
        }
    }

    private void moveGhosts() {
        for (Ghost ghost : ghosts) {
            int bestDist = Integer.MAX_VALUE;
            int bestRow = ghost.row;
            int bestCol = ghost.col;

            int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
            for (int[] d : dirs) {
                int nr = ghost.row + d[0];
                int nc = ghost.col + d[1];
                if (!isWalkable(nr, nc)) continue;

                int dist = Math.abs(playerRow - nr) + Math.abs(playerCol - nc);
                if (dist < bestDist || (dist == bestDist && random.nextBoolean())) {
                    bestDist = dist;
                    bestRow = nr;
                    bestCol = nc;
                }
            }

            if (random.nextDouble() < 0.2) {
                List<int[]> options = new ArrayList<>();
                for (int[] d : dirs) {
                    int nr = ghost.row + d[0];
                    int nc = ghost.col + d[1];
                    if (isWalkable(nr, nc)) options.add(new int[]{nr, nc});
                }
                if (!options.isEmpty()) {
                    int[] any = options.get(random.nextInt(options.size()));
                    bestRow = any[0];
                    bestCol = any[1];
                }
            }

            ghost.row = bestRow;
            ghost.col = bestCol;
        }
    }

    private void checkCollision() {
        for (Ghost ghost : ghosts) {
            if (ghost.row == playerRow && ghost.col == playerCol) {
                lives--;
                playerRow = 1;
                playerCol = 1;
                desiredDirection = Direction.RIGHT;
                break;
            }
        }
    }

    private boolean isWalkable(int r, int c) {
        if (r < 0 || c < 0 || r >= map.length || c >= map[0].length) return false;
        return map[r][c] != '#';
    }

    private char[][] buildMap() {
        String[] rows = new String[] {
                "####################",
                "#........#.........#",
                "#.####...#...####..#",
                "#.................##",
                "#.####.#.###.#.##..#",
                "#......#.....#.....#",
                "###.###########.####",
                "#..................#",
                "#.#####.###.#####..#",
                "#.......# #........#",
                "#..###..###..###...#",
                "#..................#",
                "####################"
        };
        char[][] out = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            out[i] = rows[i].toCharArray();
        }
        return out;
    }
}
