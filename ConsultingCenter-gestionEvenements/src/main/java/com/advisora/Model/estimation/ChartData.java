package com.advisora.Model.estimation;

import java.util.List;

public record ChartData(
        String type,
        List<ChartPoint> points
) {
}
