package com.advisora.GUI.Game.impl.chess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChessAi {

    public Move chooseMove(Board board, int depth) {
        try {
            List<Move> legal = board.legalMoves();
            if (legal == null || legal.isEmpty()) {
                return null;
            }

            Side maxSide = board.getSideToMove();
            int bestScore = Integer.MIN_VALUE;
            List<Move> bestMoves = new ArrayList<>();

            for (Move move : legal) {
                board.doMove(move);
                int score = minimax(board, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, maxSide);
                board.undoMove();
                if (score > bestScore) {
                    bestScore = score;
                    bestMoves.clear();
                    bestMoves.add(move);
                } else if (score == bestScore) {
                    bestMoves.add(move);
                }
            }

            if (bestMoves.isEmpty()) {
                return legal.get(ThreadLocalRandom.current().nextInt(legal.size()));
            }
            return bestMoves.get(ThreadLocalRandom.current().nextInt(bestMoves.size()));
        } catch (Exception e) {
            try {
                List<Move> legal = board.legalMoves();
                if (legal != null && !legal.isEmpty()) {
                    return legal.get(ThreadLocalRandom.current().nextInt(legal.size()));
                }
            } catch (Exception ignored) {
                // ignore
            }
            return null;
        }
    }

    private int minimax(Board board, int depth, int alpha, int beta, Side maxSide) throws Exception {
        if (depth <= 0 || board.isMated() || board.isDraw()) {
            return evaluate(board, maxSide);
        }

        List<Move> legal = board.legalMoves();
        if (legal == null || legal.isEmpty()) {
            return evaluate(board, maxSide);
        }

        boolean maximizing = board.getSideToMove() == maxSide;
        if (maximizing) {
            int value = Integer.MIN_VALUE;
            for (Move move : legal) {
                board.doMove(move);
                value = Math.max(value, minimax(board, depth - 1, alpha, beta, maxSide));
                board.undoMove();
                alpha = Math.max(alpha, value);
                if (alpha >= beta) break;
            }
            return value;
        }

        int value = Integer.MAX_VALUE;
        for (Move move : legal) {
            board.doMove(move);
            value = Math.min(value, minimax(board, depth - 1, alpha, beta, maxSide));
            board.undoMove();
            beta = Math.min(beta, value);
            if (alpha >= beta) break;
        }
        return value;
    }

    private int evaluate(Board board, Side maxSide) {
        if (board.isMated()) {
            return board.getSideToMove() == maxSide ? -100000 : 100000;
        }
        int score = 0;
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece piece = board.getPiece(square);
            if (piece == Piece.NONE) continue;
            int value = pieceValue(piece.getPieceType());
            if (piece.getPieceSide() == maxSide) {
                score += value;
            } else {
                score -= value;
            }
        }
        return score;
    }

    private int pieceValue(PieceType type) {
        return switch (type) {
            case PAWN -> 100;
            case KNIGHT, BISHOP -> 320;
            case ROOK -> 500;
            case QUEEN -> 900;
            case KING -> 20000;
            default -> 0;
        };
    }
}
