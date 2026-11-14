package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The report card of a student for one year: a summary per subject, the general average of the
 * student in each period (across all its subjects) and the yearly average (null if no grades).
 */
public record YearReport(int year, String gradeName, List<SubjectSummary> subjects,
                         Map<AcademicPeriod, BigDecimal> periodAverages, BigDecimal yearAverage) {
}
