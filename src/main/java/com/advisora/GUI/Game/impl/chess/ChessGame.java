package com.advisora.GUI.Game.impl.chess;

import com.advisora.GUI.Game.api.PlayableGame;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

public class ChessGame implements PlayableGame {
    private final Board board = new Board();
    private final ChessAi ai = new ChessAi();

    private final Button[][] squares = new Button[8][8];
    private final Label statusLabel = new Label();
    private final ComboBox<String> modeBox = new ComboBox<>();

    private BorderPane root;
    private Square selectedSquare;
    private List<Square> legalTargets = new ArrayList<>();

    @Override
    public String id() {
        return "chess";
    }

    @Override
    public String displayName() {
        return "Chess";
    }

    @Override
    public Node createView() {
        if (root != null) {
            return root;
        }

        modeBox.setItems(FXCollections.observableArrayList("Local 2 joueurs", "Vs ordinateur"));
        modeBox.getSelectionModel().select(0);

        Label modeLabel = new Label("Mode:");
        Label helpLabel = new Label("Astuce: cliquez une piece, puis la case cible.");
        helpLabel.getStyleClass().add("game-status");
        Label legendLabel = new Label("Blanc: roi dame tour fou cavalier pion | Noir: roi dame tour fou cavalier pion");
        legendLabel.getStyleClass().add("game-status");
        HBox top = new HBox(10, modeLabel, modeBox, statusLabel, helpLabel, legendLabel);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 10, 0));

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Button tile = new Button();
                tile.setMinSize(62, 62);
                tile.setMaxSize(62, 62);
                tile.getStyleClass().add("chess-cell");
                if ((row + col) % 2 == 0) {
                    tile.getStyleClass().add("chess-light");
                } else {
                    tile.getStyleClass().add("chess-dark");
                }
                final int rr = row;
                final int cc = col;
                tile.setOnAction(e -> onSquareClicked(rr, cc));
                squares[row][col] = tile;
                grid.add(tile, col, row);
            }
        }

        root = new BorderPane();
        root.getStyleClass().add("chess-root");
        root.setTop(top);
        root.setCenter(new StackPane(grid));

        reset();
        return root;
    }

    @Override
    public void start() {
        refreshBoard();
    }

    @Override
    public void reset() {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        selectedSquare = null;
        legalTargets = new ArrayList<>();
        refreshBoard();
    }

    @Override
    public void stop() {
        // no background thread
    }

    @Override
    public String statusText() {
        if (board.isMated()) {
            return "Echec et mat - " + (board.getSideToMove() == Side.WHITE ? "Noir" : "Blanc") + " gagne";
        }
        if (board.isDraw()) {
            return "Partie nulle";
        }
        return "Tour: " + (board.getSideToMove() == Side.WHITE ? "Blanc" : "Noir");
    }

    private void onSquareClicked(int row, int col) {
        Square clicked = squareFor(row, col);
        if (selectedSquare == null) {
            Piece piece = board.getPiece(clicked);
            if (piece == Piece.NONE || piece.getPieceSide() != board.getSideToMove()) {
                return;
            }
            selectedSquare = clicked;
            legalTargets = legalMovesFrom(clicked);
            refreshBoard();
            return;
        }

        if (clicked == selectedSquare) {
            selectedSquare = null;
            legalTargets = new ArrayList<>();
            refreshBoard();
            return;
        }

        if (!legalTargets.contains(clicked)) {
            selectedSquare = null;
            legalTargets = new ArrayList<>();
            refreshBoard();
            return;
        }

        Move move = findLegalMove(selectedSquare, clicked);
        if (move == null) {
            selectedSquare = null;
            legalTargets = new ArrayList<>();
            refreshBoard();
            return;
        }
        try {
            board.doMove(move);
        } catch (Exception e) {
            selectedSquare = null;
            legalTargets = new ArrayList<>();
            refreshBoard();
            return;
        }

        selectedSquare = null;
        legalTargets = new ArrayList<>();
        refreshBoard();

        if ("Vs ordinateur".equals(modeBox.getValue()) && !board.isMated() && !board.isDraw()) {
            Move aiMove = ai.chooseMove(board, 2);
            if (aiMove != null) {
                try {
                    board.doMove(aiMove);
                } catch (Exception ignored) {
                    // fallback already handled in ai
                }
            }
            refreshBoard();
        }
    }

    private List<Square> legalMovesFrom(Square from) {
        List<Square> targets = new ArrayList<>();
        try {
            List<Move> legal = board.legalMoves();
            for (Move move : legal) {
                if (move.getFrom() == from) {
                    targets.add(move.getTo());
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return targets;
    }

    private Move findLegalMove(Square from, Square to) {
        try {
            List<Move> legal = board.legalMoves();
            for (Move move : legal) {
                if (move.getFrom() == from && move.getTo() == to) {
                    return move;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private Square squareFor(int row, int col) {
        String file = String.valueOf((char) ('A' + col));
        String rank = String.valueOf(8 - row);
        return Square.valueOf(file + rank);
    }

    private void refreshBoard() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Square sq = squareFor(row, col);
                Button cell = squares[row][col];
                Piece piece = board.getPiece(sq);
                cell.setText(pieceToSymbol(piece));
                cell.getStyleClass().removeAll(
                        "chess-selected",
                        "chess-target",
                        "chess-piece-white",
                        "chess-piece-black",
                        "chess-outline-light",
                        "chess-outline-dark"
                );
                if (piece != Piece.NONE) {
                    boolean lightSquare = (row + col) % 2 == 0;
                    if (piece.getPieceSide() == Side.WHITE) {
                        cell.getStyleClass().add("chess-piece-white");
                        if (lightSquare) {
                            cell.getStyleClass().add("chess-outline-dark");
                        }
                    } else {
                        cell.getStyleClass().add("chess-piece-black");
                        if (!lightSquare) {
                            cell.getStyleClass().add("chess-outline-light");
                        }
                    }
                }
                if (sq == selectedSquare) {
                    cell.getStyleClass().add("chess-selected");
                } else if (legalTargets.contains(sq)) {
                    cell.getStyleClass().add("chess-target");
                }
            }
        }
        statusLabel.setText(statusText());
    }

    private String pieceToSymbol(Piece piece) {
        return switch (piece) {
            case WHITE_KING -> "\u2654";
            case WHITE_QUEEN -> "\u2655";
            case WHITE_ROOK -> "\u2656";
            case WHITE_BISHOP -> "\u2657";
            case WHITE_KNIGHT -> "\u2658";
            case WHITE_PAWN -> "\u2659";
            case BLACK_KING -> "\u265A";
            case BLACK_QUEEN -> "\u265B";
            case BLACK_ROOK -> "\u265C";
            case BLACK_BISHOP -> "\u265D";
            case BLACK_KNIGHT -> "\u265E";
            case BLACK_PAWN -> "\u265F";
            default -> "";
        };
    }
}

