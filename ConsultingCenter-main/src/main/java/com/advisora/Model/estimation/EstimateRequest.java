package com.advisora.Model.estimation;

import com.advisora.enums.ScopeSize;

public record EstimateRequest(
        String category,
        ScopeSize scope,
        int complexity,
        int durationDays,
        String country,
        String displayCurrency,
        Double budgetCap
) {
}

