package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Final grade = arithmetic mean of the four periods (every period weighs the same). */
public final class SimpleAverageStrategy implements FinalGradeStrategy {

    @Override
    public String getName() {
        return "promedio simple";
    }

    @Override
    public BigDecimal compute(Map<AcademicPeriod, BigDecimal> periodGrades) {
        BigDecimal sum = AcademicPeriod.teachingPeriods().stream()
                .map(periodGrades::get)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(AcademicPeriod.teachingPeriods().size()), 1, RoundingMode.HALF_UP);
    }
}
