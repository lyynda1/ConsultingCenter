package game;

import com.advisora.GUI.Game.api.PlayableGame;
import com.advisora.Services.game.GameRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class test_gamehub_reset_back {

    @Test
    void games_have_stable_metadata() {
        GameRegistry registry = new GameRegistry();

        for (String id : new String[]{"g2048", "chess", "pacman", "tictactoe", "snake"}) {
            PlayableGame game = registry.build(id);
            assertNotNull(game.id());
            assertFalse(game.id().isBlank());
            assertNotNull(game.displayName());
            assertNotNull(game.statusText());
        }
    }
}
