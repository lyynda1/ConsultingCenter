package com.advisora.Services.estimation;

import com.advisora.Model.estimation.ChartData;
import com.advisora.Model.estimation.ChartPoint;
import com.advisora.Model.estimation.CostRange;
import com.advisora.Model.estimation.DurationRange;
import com.advisora.Model.estimation.EstimateRequest;
import com.advisora.Model.estimation.EstimateResponse;
import com.advisora.Model.estimation.FxInfo;
import com.advisora.enums.EstimationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EstimateService {
    private static final List<String> ALLOWED_CURRENCIES = List.of("EUR", "TND", "USD");

    private final EstimationRuleEngine ruleEngine;
    private final FxRateService fxRateService;

    public EstimateService() {
        this(new EstimationRuleEngine(), new FxRateService());
    }

    public EstimateService(EstimationRuleEngine ruleEngine, FxRateService fxRateService) {
        this.ruleEngine = ruleEngine;
        this.fxRateService = fxRateService;
    }

    public EstimateResponse estimate(EstimateRequest request) {
        validate(request);

        EstimationRuleEngine.EstimationDraft draft = ruleEngine.compute(request);
        String targetCurrency = request.displayCurrency().trim().toUpperCase(Locale.ROOT);

        double rate = 1.0;
        FxInfo fxInfo = new FxInfo(false, null, null);
        if (!"EUR".equals(targetCurrency)) {
            FxRateService.FxQuote fxQuote = fxRateService.getRate("EUR", targetCurrency);
            rate = fxQuote.rate();
            fxInfo = new FxInfo(true, round6(rate), fxQuote.date());
        }

        CostRange cost = new CostRange(
                round2(draft.minCostEur() * rate),
                round2(draft.p50CostEur() * rate),
                round2(draft.maxCostEur() * rate),
                targetCurrency
        );

        DurationRange duration = new DurationRange(
                draft.minDurationDays(),
                draft.p50DurationDays(),
                draft.maxDurationDays()
        );

        List<ChartPoint> convertedPoints = new ArrayList<>();
        if ("LAUNCH_READINESS".equalsIgnoreCase(draft.chartType())) {
            convertedPoints.addAll(draft.chartPointsEur());
        } else {
            for (ChartPoint point : draft.chartPointsEur()) {
                convertedPoints.add(new ChartPoint(point.x(), round2(point.y() * rate)));
            }
        }

        ChartData chartData = new ChartData(draft.chartType(), List.copyOf(convertedPoints));
        boolean canPublish = draft.status() != EstimationStatus.BLOCKED;

        return new EstimateResponse(
                canPublish,
                draft.status(),
                draft.score(),
                draft.riskLevel(),
                cost,
                duration,
                draft.recommendations(),
                chartData,
                fxInfo
        );
    }

    private void validate(EstimateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.category() == null || request.category().isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        if (request.scope() == null) {
            throw new IllegalArgumentException("scope is required");
        }
        if (request.complexity() < 1 || request.complexity() > 5) {
            throw new IllegalArgumentException("complexity must be between 1 and 5");
        }
        if (request.durationDays() <= 0) {
            throw new IllegalArgumentException("durationDays must be > 0");
        }
        if (request.country() == null || request.country().isBlank()) {
            throw new IllegalArgumentException("country is required");
        }
        if (request.displayCurrency() == null || request.displayCurrency().isBlank()) {
            throw new IllegalArgumentException("displayCurrency is required");
        }
        String normalizedCurrency = request.displayCurrency().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CURRENCIES.contains(normalizedCurrency)) {
            throw new IllegalArgumentException("displayCurrency must be one of EUR/TND/USD");
        }
        if (request.budgetCap() == null) {
            throw new IllegalArgumentException("budgetCap is required");
        }
        if (request.budgetCap() < 0) {
            throw new IllegalArgumentException("budgetCap must be >= 0");
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round6(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
