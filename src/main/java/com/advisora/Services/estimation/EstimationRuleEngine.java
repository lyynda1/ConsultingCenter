package com.advisora.Services.estimation;

import com.advisora.Model.estimation.ChartPoint;
import com.advisora.Model.estimation.EstimateRequest;
import com.advisora.enums.EstimationStatus;
import com.advisora.enums.RiskLevel;
import com.advisora.enums.ScopeSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EstimationRuleEngine {
    static final String ENGINE_VERSION = "v2";

    public EstimationDraft compute(EstimateRequest request) {
        double categoryBase = categoryBase(request.category());
        double scopeMultiplier = scopeMultiplier(request.scope());
        double complexityFactor = 1.0 + ((request.complexity() - 3) * 0.20);
        double durationFactor = 1.0 + clamp((request.durationDays() - 30) / 120.0, -0.22, 0.85);

        double p50BaseEur = categoryBase * scopeMultiplier * complexityFactor * durationFactor;
        double uncertaintyDown = 0.12 + (request.complexity() * 0.02) + (request.scope() == ScopeSize.L ? 0.03 : 0.0);
        double uncertaintyUp = 0.20 + (request.complexity() * 0.03) + (request.scope() == ScopeSize.L ? 0.04 : 0.0);
        double minCostEur = p50BaseEur * (1.0 - uncertaintyDown);
        double maxCostEur = p50BaseEur * (1.0 + uncertaintyUp);

        int minDays = Math.max(1, (int) Math.round(request.durationDays() * 0.88));
        int p50Days = request.durationDays();
        int maxDays = Math.max(p50Days + 1, (int) Math.round(request.durationDays() * (1.16 + request.complexity() * 0.04)));

        int score = scoreFrom(request, p50BaseEur);
        EstimationStatus status = statusFromScore(score);
        double budgetCoverage = request.budgetCap() == null ? 1.0 : request.budgetCap() / Math.max(1.0, p50BaseEur);

        if (request.budgetCap() != null) {
            if (request.budgetCap() < minCostEur) {
                score = Math.max(0, score - 25);
                status = worsenStatus(status, EstimationStatus.BLOCKED);
                budgetCoverage = request.budgetCap() / Math.max(1.0, p50BaseEur);
            } else if (request.budgetCap() < p50BaseEur) {
                score = Math.max(0, score - 12);
                status = worsenStatus(status, EstimationStatus.WARNING);
                budgetCoverage = request.budgetCap() / Math.max(1.0, p50BaseEur);
            }
        }

        RiskLevel riskLevel = riskFromScore(score);
        List<String> recommendations = recommendationsFor(request, score, status, budgetCoverage);
        List<ChartPoint> chartPoints = buildLaunchReadinessChart(request, score, budgetCoverage);

        return new EstimationDraft(
                Math.max(0, score >= 0 ? score : 0),
                status,
                riskLevel,
                round2(minCostEur),
                round2(p50BaseEur),
                round2(maxCostEur),
                minDays,
                p50Days,
                maxDays,
                List.copyOf(recommendations),
                chartPoints,
                "LAUNCH_READINESS"
        );
    }

    private int scoreFrom(EstimateRequest request, double p50BaseEur) {
        int score = 86;
        score -= request.complexity() * 7;
        score -= Math.abs(request.durationDays() - 50) / 5;
        if (request.scope() == ScopeSize.L) score -= 7;
        if (request.durationDays() > 100) score -= 10;
        if (p50BaseEur > 35000) score -= 6;
        if (p50BaseEur > 55000) score -= 6;
        return Math.max(0, Math.min(100, score));
    }

    private List<String> recommendationsFor(EstimateRequest request, int score, EstimationStatus status, double budgetCoverage) {
        List<String> recs = new ArrayList<>();

        if (status == EstimationStatus.OK) {
            recs.add("Decision: GO. Le projet peut etre lance avec un suivi de pilotage standard.");
        } else if (status == EstimationStatus.WARNING) {
            recs.add("Decision: GO sous conditions. Verifier les pre-requis critiques avant lancement.");
        } else {
            recs.add("Decision: NO-GO temporaire. Finaliser les actions correctives avant lancement.");
        }

        if (budgetCoverage < 0.85) {
            recs.add("Ajuster le cadrage: augmenter les moyens ou reduire le perimetre du premier lot.");
        } else if (budgetCoverage < 1.0) {
            recs.add("Budget serre: prioriser un lot 1 focalise sur la valeur metier principale.");
        } else if (budgetCoverage < 1.15) {
            recs.add("Prevoir une marge de securite pour absorber les aleas de lancement.");
        }

        if (request.scope() == ScopeSize.L) {
            recs.add("Decouper le lancement en phases avec un gate de validation entre chaque phase.");
        }
        if (request.complexity() >= 4) {
            recs.add("Mettre en place une gouvernance projet renforcee avec suivi des risques hebdomadaire.");
        }
        if (request.durationDays() < 35 || request.durationDays() > 90) {
            recs.add("Valider le planning cible avec les equipes avant de lancer le projet.");
        }

        if (status == EstimationStatus.BLOCKED) {
            recs.add("Organiser un atelier de recadrage metier/technique puis refaire l'estimation.");
        } else if (status == EstimationStatus.WARNING) {
            recs.add("Realiser une preuve de faisabilite courte avant engagement complet.");
        } else {
            recs.add("Lancer avec un tableau de bord KPI simple: avancement, risques, qualite.");
        }

        if (score < 45) {
            recs.add("Prioriser strictement les exigences critiques pour reduire l'exposition initiale.");
        } else if (score >= 70) {
            recs.add("Le niveau de preparation est bon: maintenir le plan et les checkpoints de pilotage.");
        }

        trimToMax(recs, 5);
        if (recs.isEmpty()) {
            recs.add("Conserver un suivi hebdomadaire pour controler la derive cout/delai.");
        }
        return recs;
    }

    private List<ChartPoint> buildLaunchReadinessChart(EstimateRequest request, int globalScore, double budgetCoverage) {
        int strategicClarity = clampToPercent(globalScore + (request.scope() == ScopeSize.S ? 8 : request.scope() == ScopeSize.M ? 3 : -4));
        int budgetReadiness = clampToPercent((int) Math.round(100 * Math.min(1.2, Math.max(0.0, budgetCoverage))) - 5);
        int executionReadiness = clampToPercent(globalScore - (request.complexity() - 3) * 8);
        int governanceReadiness = clampToPercent(globalScore - (request.scope() == ScopeSize.L ? 7 : 2));
        int riskControl = clampToPercent(globalScore - request.complexity() * 5 + 18);

        return List.of(
                new ChartPoint(1, strategicClarity),
                new ChartPoint(2, budgetReadiness),
                new ChartPoint(3, executionReadiness),
                new ChartPoint(4, governanceReadiness),
                new ChartPoint(5, riskControl)
        );
    }

    private void trimToMax(List<String> recs, int max) {
        while (recs.size() > max) {
            recs.remove(recs.size() - 1);
        }
    }

    private double categoryBase(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "IT", "DIGITAL", "SOFTWARE" -> 12000.0;
            case "FINANCE", "FINTECH" -> 11000.0;
            case "MARKETING" -> 9000.0;
            case "HEALTH", "HEALTHCARE" -> 13000.0;
            case "ECOMMERCE" -> 10500.0;
            case "INDUSTRY" -> 14000.0;
            case "EDUCATION" -> 9500.0;
            case "LOGISTICS" -> 12500.0;
            case "CONSTRUCTION" -> 15000.0;
            case "PUBLIC" -> 11500.0;
            default -> 10000.0;
        };
    }

    private double scopeMultiplier(ScopeSize scope) {
        return switch (scope) {
            case S -> 0.75;
            case M -> 1.00;
            case L -> 1.40;
        };
    }

    private RiskLevel riskFromScore(int score) {
        if (score >= 70) return RiskLevel.LOW;
        if (score >= 45) return RiskLevel.MEDIUM;
        return RiskLevel.HIGH;
    }

    private EstimationStatus statusFromScore(int score) {
        if (score >= 70) return EstimationStatus.OK;
        if (score >= 45) return EstimationStatus.WARNING;
        return EstimationStatus.BLOCKED;
    }

    private EstimationStatus worsenStatus(EstimationStatus current, EstimationStatus candidate) {
        if (current == EstimationStatus.BLOCKED || candidate == EstimationStatus.BLOCKED) return EstimationStatus.BLOCKED;
        if (current == EstimationStatus.WARNING || candidate == EstimationStatus.WARNING) return EstimationStatus.WARNING;
        return EstimationStatus.OK;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int clampToPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public record EstimationDraft(
            int score,
            EstimationStatus status,
            RiskLevel riskLevel,
            double minCostEur,
            double p50CostEur,
            double maxCostEur,
            int minDurationDays,
            int p50DurationDays,
            int maxDurationDays,
            List<String> recommendations,
            List<ChartPoint> chartPointsEur,
            String chartType
    ) {
    }
}

