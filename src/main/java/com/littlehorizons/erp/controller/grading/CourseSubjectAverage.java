package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.model.entity.Subject;

import java.math.BigDecimal;

/** Statistics of one subject in one period for a whole course (all the students of a grade). */
public record CourseSubjectAverage(Subject subject, BigDecimal average, BigDecimal lowest,
                                   BigDecimal highest, int students) {
}
