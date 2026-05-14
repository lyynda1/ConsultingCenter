package decision;

import com.advisora.enums.DecisionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class test_decision {

    @Test
    void decision_status_from_db_accepts_case_insensitive_values() {
        assertEquals(DecisionStatus.PENDING, DecisionStatus.fromDb("pending"));
        assertEquals(DecisionStatus.ACTIVE, DecisionStatus.fromDb("ACTIVE"));
        assertEquals(DecisionStatus.REFUSED, DecisionStatus.fromDb("Refused"));
    }

    @Test
    void decision_status_from_db_fallbacks_to_pending() {
        assertEquals(DecisionStatus.PENDING, DecisionStatus.fromDb("unknown"));
        assertEquals(DecisionStatus.PENDING, DecisionStatus.fromDb(null));
        assertEquals(DecisionStatus.PENDING, DecisionStatus.fromDb("   "));
    }
}

