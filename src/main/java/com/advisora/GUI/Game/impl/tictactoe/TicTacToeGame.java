package com.advisora.GUI.Game.impl.tictactoe;

import com.advisora.GUI.Game.api.PlayableGame;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class TicTacToeGame implements PlayableGame {
    private final TicTacToeLogic logic = new TicTacToeLogic();
    private final Button[][] buttons = new Button[3][3];
    private final Label status = new Label();
    private final ComboBox<String> modeBox = new ComboBox<>();
    private BorderPane root;

    @Override
    public String id() {
        return "tictactoe";
    }

    @Override
    public String displayName() {
        return "Tic-Tac-Toe";
    }

    @Override
    public Node createView() {
        if (root != null) return root;

        modeBox.getItems().setAll("Local 2 joueurs", "Vs ordinateur");
        modeBox.getSelectionModel().select("Local 2 joueurs");
        modeBox.valueProperty().addListener((obs, oldV, newV) -> reset());

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(8);
        grid.setVgap(8);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                Button b = new Button(" ");
                b.setMinSize(90, 90);
                b.getStyleClass().add("ttt-cell");
                final int rr = r;
                final int cc = c;
                b.setOnAction(e -> {
                    if (logic.play(rr, cc)) {
                        refresh();
                        playComputerIfNeeded();
                    }
                });
                buttons[r][c] = b;
                grid.add(b, c, r);
            }
        }

        status.getStyleClass().add("game-status");
        Label modeLabel = new Label("Mode:");
        HBox top = new HBox(10, modeLabel, modeBox, status);
        top.setAlignment(Pos.CENTER_LEFT);

        root = new BorderPane();
        root.getStyleClass().add("ttt-root");
        root.setTop(top);
        root.setCenter(grid);

        reset();
        return root;
    }

    @Override
    public void start() {
        refresh();
    }

    @Override
    public void reset() {
        logic.reset();
        refresh();
    }

    @Override
    public void stop() {
        // nothing background
    }

    @Override
    public String statusText() {
        if (logic.getWinner() != ' ') {
            return "Gagnant: " + logic.getWinner();
        }
        if (logic.isDraw()) {
            return "Match nul";
        }
        return "Tour: " + logic.getCurrent();
    }

    private void playComputerIfNeeded() {
        if (!"Vs ordinateur".equals(modeBox.getValue())) return;
        if (logic.getWinner() != ' ' || logic.isDraw()) return;
        if (logic.getCurrent() != 'O') return;

        int[] move = bestMoveForO(logic.snapshot());
        if (move != null) {
            logic.play(move[0], move[1]);
            refresh();
        }
    }

    private int[] bestMoveForO(char[][] board) {
        int bestScore = Integer.MIN_VALUE;
        int[] best = null;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (board[r][c] != ' ') continue;
                board[r][c] = 'O';
                int score = minimax(board, false);
                board[r][c] = ' ';
                if (score > bestScore) {
                    bestScore = score;
                    best = new int[]{r, c};
                }
            }
        }
        return best;
    }

    private int minimax(char[][] board, boolean oTurn) {
        Character winner = winnerOf(board);
        if (winner != null) {
            if (winner == 'O') return 10;
            if (winner == 'X') return -10;
        }
        if (isFull(board)) return 0;

        if (oTurn) {
            int best = Integer.MIN_VALUE;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    if (board[r][c] != ' ') continue;
                    board[r][c] = 'O';
                    best = Math.max(best, minimax(board, false));
                    board[r][c] = ' ';
                }
            }
            return best;
        }

        int best = Integer.MAX_VALUE;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (board[r][c] != ' ') continue;
                board[r][c] = 'X';
                best = Math.min(best, minimax(board, true));
                board[r][c] = ' ';
            }
        }
        return best;
    }

    private Character winnerOf(char[][] b) {
        for (int i = 0; i < 3; i++) {
            if (b[i][0] != ' ' && b[i][0] == b[i][1] && b[i][1] == b[i][2]) return b[i][0];
            if (b[0][i] != ' ' && b[0][i] == b[1][i] && b[1][i] == b[2][i]) return b[0][i];
        }
        if (b[0][0] != ' ' && b[0][0] == b[1][1] && b[1][1] == b[2][2]) return b[0][0];
        if (b[0][2] != ' ' && b[0][2] == b[1][1] && b[1][1] == b[2][0]) return b[0][2];
        return null;
    }

    private boolean isFull(char[][] b) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (b[r][c] == ' ') return false;
            }
        }
        return true;
    }

    private void refresh() {
        char[][] s = logic.snapshot();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                char v = s[r][c];
                Button b = buttons[r][c];
                b.setText(v == ' ' ? "" : String.valueOf(v));
                b.getStyleClass().removeAll("ttt-x", "ttt-o");
                if (v == 'X') b.getStyleClass().add("ttt-x");
                if (v == 'O') b.getStyleClass().add("ttt-o");
            }
        }
        status.setText(statusText());
    }
}

