package com.advisora.Model.estimation;

public record CostRange(
        double min,
        double p50,
        double max,
        String currency
) {
}
