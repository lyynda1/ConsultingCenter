package estimation;

import com.advisora.Model.estimation.EstimateRequest;
import com.advisora.Services.estimation.EstimationRuleEngine;
import com.advisora.enums.ScopeSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class test_estimation_rule_engine {

    @Test
    void computes_consistent_ranges_and_bounded_score() {
        EstimationRuleEngine engine = new EstimationRuleEngine();
        EstimateRequest req = new EstimateRequest("IT", ScopeSize.M, 4, 45, "TN", "EUR", 30000.0);

        EstimationRuleEngine.EstimationDraft draft = engine.compute(req);

        assertTrue(draft.minCostEur() <= draft.p50CostEur());
        assertTrue(draft.p50CostEur() <= draft.maxCostEur());
        assertTrue(draft.score() >= 0 && draft.score() <= 100);
        assertTrue(draft.minDurationDays() <= draft.p50DurationDays());
        assertTrue(draft.p50DurationDays() <= draft.maxDurationDays());
        assertEquals(5, draft.chartPointsEur().size());
    }
}
