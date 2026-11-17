package com.littlehorizons.erp.controller.report;

import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.GradeLevel;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Pure calculations for the general overview, written with Streams (no database, no console). */
public final class OverviewAnalyzer {

    private OverviewAnalyzer() {
    }

    /**
     * How many enrollments each grade has, from the lowest grade to the highest. Only the grades
     * that have at least one enrollment appear (a grade without students is not interesting here).
     */
    public static Map<String, Long> enrolledByGrade(List<Enrollment> enrollments) {
        return enrollments.stream()
                .collect(Collectors.groupingBy(Enrollment::getGradeLevel,
                        () -> new TreeMap<>(Comparator.comparingInt(GradeLevel::getLevel)),
                        Collectors.counting()))
                .entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getName(), Map.Entry::getValue,
                        Long::sum, LinkedHashMap::new));
    }
}
