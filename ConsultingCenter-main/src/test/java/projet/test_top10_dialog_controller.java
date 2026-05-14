package projet;

import com.advisora.GUI.Project.Top10DialogController;
import com.advisora.Model.projet.Top10Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_top10_dialog_controller {

    @Test
    void empty_input_is_rejected() throws Exception {
        Top10DialogController controller = new Top10DialogController();
        Method normalize = Top10DialogController.class.getDeclaredMethod("normalizeCategoryInput", String.class);
        normalize.setAccessible(true);

        Object out1 = normalize.invoke(controller, "");
        Object out2 = normalize.invoke(controller, "   ");
        assertNull(out1);
        assertNull(out2);
    }

    @Test
    void valid_input_is_trimmed() throws Exception {
        Top10DialogController controller = new Top10DialogController();
        Method normalize = Top10DialogController.class.getDeclaredMethod("normalizeCategoryInput", String.class);
        normalize.setAccessible(true);

        String out = (String) normalize.invoke(controller, "  fintech  ");
        assertEquals("fintech", out);
    }

    @Test
    void info_line_uses_category() throws Exception {
        Top10DialogController controller = new Top10DialogController();
        Method info = Top10DialogController.class.getDeclaredMethod("buildInfoLine", Top10Response.class);
        info.setAccessible(true);

        Top10Response response = new Top10Response();
        response.setCategory("Streaming");
        String line = (String) info.invoke(controller, response);
        assertTrue(line.contains("Streaming"));
    }
}
