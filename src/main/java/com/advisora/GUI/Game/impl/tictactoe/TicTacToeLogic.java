package com.advisora.GUI.Game.impl.tictactoe;

public class TicTacToeLogic {
    private final char[][] board = new char[3][3];
    private char current = 'X';
    private char winner = ' ';
    private boolean draw;

    public void reset() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                board[r][c] = ' ';
            }
        }
        current = 'X';
        winner = ' ';
        draw = false;
    }

    public boolean play(int row, int col) {
        if (winner != ' ' || draw) return false;
        if (row < 0 || col < 0 || row >= 3 || col >= 3) return false;
        if (board[row][col] != ' ') return false;

        board[row][col] = current;
        if (isWinner(current)) {
            winner = current;
        } else if (isBoardFull()) {
            draw = true;
        } else {
            current = current == 'X' ? 'O' : 'X';
        }
        return true;
    }

    public char getCurrent() {
        return current;
    }

    public char getWinner() {
        return winner;
    }

    public boolean isDraw() {
        return draw;
    }

    public char[][] snapshot() {
        char[][] out = new char[3][3];
        for (int r = 0; r < 3; r++) {
            System.arraycopy(board[r], 0, out[r], 0, 3);
        }
        return out;
    }

    private boolean isWinner(char p) {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == p && board[i][1] == p && board[i][2] == p) return true;
            if (board[0][i] == p && board[1][i] == p && board[2][i] == p) return true;
        }
        return (board[0][0] == p && board[1][1] == p && board[2][2] == p)
                || (board[0][2] == p && board[1][1] == p && board[2][0] == p);
    }

    private boolean isBoardFull() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (board[r][c] == ' ') return false;
            }
        }
        return true;
    }
}

