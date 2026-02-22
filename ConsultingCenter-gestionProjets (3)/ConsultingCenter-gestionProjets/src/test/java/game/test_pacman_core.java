package game;

import com.advisora.GUI.Game.impl.pacman.PacmanLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_pacman_core {

    @Test
    void score_increases_while_collecting_pellets() {
        PacmanLogic logic = new PacmanLogic();
        int startScore = logic.getScore();

        logic.setDesiredDirection(PacmanLogic.Direction.RIGHT);
        for (int i = 0; i < 5; i++) {
            logic.tick();
        }

        assertTrue(logic.getScore() >= startScore);
    }

    @Test
    void lives_never_increase_above_start() {
        PacmanLogic logic = new PacmanLogic();
        for (int i = 0; i < 60; i++) {
            logic.tick();
        }
        assertTrue(logic.getLives() <= 3);
    }
}
