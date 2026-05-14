package game;

import com.advisora.GUI.HomeController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_home_to_gamehub_navigation {

    @Test
    void home_game_button_triggers_callback() throws Exception {
        HomeController controller = new HomeController();
        AtomicBoolean called = new AtomicBoolean(false);
        controller.setOnOpenGames(() -> called.set(true));

        Method action = HomeController.class.getDeclaredMethod("onOpenGames");
        action.setAccessible(true);
        action.invoke(controller);

        assertTrue(called.get());
    }
}
