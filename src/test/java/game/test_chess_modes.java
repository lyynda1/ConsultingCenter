package game;

import com.advisora.GUI.Game.impl.chess.ChessAi;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_chess_modes {

    @Test
    void legal_move_generation_on_start_position() throws Exception {
        Board board = new Board();
        List<Move> legal = board.legalMoves();
        assertNotNull(legal);
        assertTrue(legal.size() > 0);
    }

    @Test
    void ai_returns_legal_move() throws Exception {
        Board board = new Board();
        ChessAi ai = new ChessAi();

        Move aiMove = ai.chooseMove(board, 2);
        assertNotNull(aiMove);

        List<Move> legal = board.legalMoves();
        boolean found = false;
        for (Move move : legal) {
            if (move.equals(aiMove)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
