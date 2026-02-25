package com.advisora.Model.estimation;

public record DurationRange(
        int minDays,
        int p50Days,
        int maxDays
) {
}
