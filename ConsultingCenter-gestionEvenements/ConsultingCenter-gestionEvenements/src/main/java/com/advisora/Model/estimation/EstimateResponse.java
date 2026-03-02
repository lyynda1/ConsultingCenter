package com.advisora.Model.estimation;

import com.advisora.enums.EstimationStatus;
import com.advisora.enums.RiskLevel;

import java.util.List;

public record EstimateResponse(
        boolean canPublish,
        EstimationStatus status,
        int score,
        RiskLevel riskLevel,
        CostRange cost,
        DurationRange duration,
        List<String> recommendations,
        ChartData chart,
        FxInfo fx
) {
}
