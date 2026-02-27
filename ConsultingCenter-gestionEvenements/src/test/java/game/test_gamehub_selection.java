package game;

import com.advisora.GUI.Game.api.PlayableGame;
import com.advisora.Model.game.GameDescriptor;
import com.advisora.Services.game.GameRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class test_gamehub_selection {

    @Test
    void registry_exposes_expected_games() {
        GameRegistry registry = new GameRegistry();
        List<GameDescriptor> games = registry.availableGames();
        assertEquals(5, games.size());
    }

    @Test
    void registry_builds_game_instances() {
        GameRegistry registry = new GameRegistry();
        for (GameDescriptor descriptor : registry.availableGames()) {
            PlayableGame game = registry.build(descriptor.getId());
            assertNotNull(game);
            assertNotNull(game.displayName());
        }
    }
}
