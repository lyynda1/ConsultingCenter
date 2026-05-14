package game;

import com.advisora.GUI.Game.impl.game2048.Game2048Logic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_2048_logic {

    @Test
    void reset_spawns_tiles() {
        Game2048Logic logic = new Game2048Logic();
        logic.reset();
        int[][] board = logic.snapshot();

        int nonZero = 0;
        for (int[] row : board) {
            for (int v : row) {
                if (v != 0) nonZero++;
            }
        }
        assertEquals(2, nonZero);
    }

    @Test
    void move_changes_board_in_normal_case() {
        Game2048Logic logic = new Game2048Logic();
        logic.reset();
        boolean moved = logic.move(Game2048Logic.Direction.LEFT)
                || logic.move(Game2048Logic.Direction.RIGHT)
                || logic.move(Game2048Logic.Direction.UP)
                || logic.move(Game2048Logic.Direction.DOWN);
        assertTrue(moved);
    }

    @Test
    void no_move_detection() throws Exception {
        Game2048Logic logic = new Game2048Logic();

        Field boardField = Game2048Logic.class.getDeclaredField("board");
        boardField.setAccessible(true);
        int[][] board = (int[][]) boardField.get(logic);

        int[][] full = {
                {2, 4, 2, 4},
                {4, 2, 4, 2},
                {2, 4, 2, 4},
                {4, 2, 4, 2}
        };
        for (int r = 0; r < 4; r++) {
            System.arraycopy(full[r], 0, board[r], 0, 4);
        }

        assertTrue(logic.isGameOver());
        boolean moved = logic.move(Game2048Logic.Direction.LEFT);
        assertFalse(moved);
    }
}
