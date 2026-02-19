package common;

import com.advisora.Services.SessionContext;
import com.advisora.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_session_context {

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void get_current_user_throws_when_no_session() {
        assertThrows(IllegalStateException.class, SessionContext::getCurrentUserId);
    }

    @Test
    void get_current_role_throws_when_no_session() {
        assertThrows(IllegalStateException.class, SessionContext::getCurrentRole);
    }

    @Test
    void set_current_user_rejects_invalid_id() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SessionContext.setCurrentUser(0, UserRole.CLIENT));
        assertEquals("Invalid user id", ex.getMessage());
    }

    @Test
    void set_current_user_rejects_null_role() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SessionContext.setCurrentUser(2, null));
        assertEquals("Role is required", ex.getMessage());
    }

    @Test
    void set_and_get_current_user_and_role_work() {
        SessionContext.setCurrentUser(7, UserRole.GERANT);
        assertEquals(7, SessionContext.getCurrentUserId());
        assertEquals(UserRole.GERANT, SessionContext.getCurrentRole());
    }

    @Test
    void role_helpers_work_for_client() {
        SessionContext.setCurrentUser(10, UserRole.CLIENT);
        assertTrue(SessionContext.isClient());
        assertFalse(SessionContext.isGerant());
        assertFalse(SessionContext.isManager());
    }

    @Test
    void role_helpers_work_for_gerant() {
        SessionContext.setCurrentUser(11, UserRole.GERANT);
        assertFalse(SessionContext.isClient());
        assertTrue(SessionContext.isGerant());
        assertTrue(SessionContext.isManager());
    }

    @Test
    void clear_resets_session() {
        SessionContext.setCurrentUser(4, UserRole.ADMIN);
        SessionContext.clear();
        assertThrows(IllegalStateException.class, SessionContext::getCurrentUserId);
        assertThrows(IllegalStateException.class, SessionContext::getCurrentRole);
    }
}

