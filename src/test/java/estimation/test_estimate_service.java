package estimation;

import com.advisora.Model.estimation.EstimateRequest;
import com.advisora.Model.estimation.EstimateResponse;
import com.advisora.Services.estimation.EstimateService;
import com.advisora.enums.ScopeSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_estimate_service {

    @Test
    void rejects_invalid_request() {
        EstimateService service = new EstimateService();
        EstimateRequest invalid = new EstimateRequest("", ScopeSize.M, 8, 0, "", "XXX", -1.0);
        assertThrows(IllegalArgumentException.class, () -> service.estimate(invalid));
    }

    @Test
    void rejects_missing_budget_cap() {
        EstimateService service = new EstimateService();
        EstimateRequest invalid = new EstimateRequest("IT", ScopeSize.M, 3, 45, "TN", "EUR", null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.estimate(invalid));
        assertEquals("budgetCap is required", ex.getMessage());
    }

    @Test
    void returns_full_response_for_valid_request() {
        EstimateService service = new EstimateService();
        EstimateRequest request = new EstimateRequest("IT", ScopeSize.M, 3, 45, "TN", "EUR", 30000.0);

        EstimateResponse response = service.estimate(request);
        assertTrue(response.score() >= 0 && response.score() <= 100);
        assertEquals(5, response.chart().points().size());
        assertTrue(response.cost().min() <= response.cost().p50());
        assertTrue(response.cost().p50() <= response.cost().max());
        assertEquals("EUR", response.cost().currency());
    }
}
