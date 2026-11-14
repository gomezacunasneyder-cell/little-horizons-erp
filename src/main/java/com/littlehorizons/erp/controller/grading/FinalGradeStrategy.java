package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.util.Map;

/**
 * STRATEGY pattern: the way the final grade of a subject is computed from its four period grades
 * is a school policy that can change. Each policy is one class behind this interface and the
 * controller only calls {@code compute}, so adding a policy never touches the controller
 * (Open/Closed principle). The policy in use is chosen in config.txt.
 */
public interface FinalGradeStrategy {

    /** Short name shown to the user, for example "promedio simple". */
    String getName();

    /** Receives the grades of periods I to IV and returns the final grade with one decimal. */
    BigDecimal compute(Map<AcademicPeriod, BigDecimal> periodGrades);
}
