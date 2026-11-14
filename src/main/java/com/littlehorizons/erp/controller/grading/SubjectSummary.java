package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What a student has in one subject in one year: the grade of each period, the average of the
 * periods I to IV already graded (null if none) and the Final grade (null if not given yet).
 */
public record SubjectSummary(Subject subject, Map<AcademicPeriod, BigDecimal> periodGrades,
                             BigDecimal average, BigDecimal finalGrade) {
}
