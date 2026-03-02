package com.advisora.Model.estimation;

import java.time.LocalDate;

public record FxInfo(
        boolean used,
        Double rate,
        LocalDate date
) {
}

