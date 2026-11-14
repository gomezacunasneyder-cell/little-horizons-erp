package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Final grade = weighted mean, each period with its own percentage (for example 20/20/30/30). */
public final class WeightedAverageStrategy implements FinalGradeStrategy {

    private final Map<AcademicPeriod, Integer> weights = new EnumMap<>(AcademicPeriod.class);

    /** The weights are given in the order I, II, III, IV and must be positive. */
    public WeightedAverageStrategy(List<Integer> orderedWeights) {
        List<AcademicPeriod> periods = AcademicPeriod.teachingPeriods();
        if (orderedWeights.size() != periods.size() || orderedWeights.stream().anyMatch(w -> w <= 0)) {
            throw new ConfigurationException("grading.final.weights necesita " + periods.size()
                    + " números positivos separados por coma (ejemplo: 20,20,30,30).");
        }
        for (int i = 0; i < periods.size(); i++) {
            weights.put(periods.get(i), orderedWeights.get(i));
        }
    }

    @Override
    public String getName() {
        return "promedio ponderado " + weights.values();
    }

    @Override
    public BigDecimal compute(Map<AcademicPeriod, BigDecimal> periodGrades) {
        BigDecimal weightedSum = weights.entrySet().stream()
                .map(e -> periodGrades.get(e.getKey()).multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        return weightedSum.divide(BigDecimal.valueOf(totalWeight), 1, RoundingMode.HALF_UP);
    }
}
