package com.littlehorizons.erp.controller.grading;

import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * All the average calculations of the grades module, written with Stream API and lambdas:
 * the records are filtered, grouped (groupingBy) and reduced, with no manual loops or counters.
 * They are pure functions (the same list gives the same result) so they are easy to test.
 */
public final class GradeAverages {

    private static final int AVERAGE_SCALE = 2;

    private GradeAverages() {
    }

    /** Mean with two decimals; empty if there is nothing to average. */
    public static Optional<BigDecimal> average(Stream<BigDecimal> grades) {
        List<BigDecimal> values = grades.toList();
        if (values.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(sum.divide(BigDecimal.valueOf(values.size()), AVERAGE_SCALE, RoundingMode.HALF_UP));
    }

    /** One subject: grades per period, average of periods I-IV and the Final grade. */
    static SubjectSummary summary(Subject subject, List<AcademicRecord> records) {
        Map<AcademicPeriod, BigDecimal> grades = new EnumMap<>(AcademicPeriod.class);
        records.forEach(r -> grades.put(r.getPeriod(), r.getGrade()));
        BigDecimal average = average(AcademicPeriod.teachingPeriods().stream()
                .map(grades::get)
                .filter(java.util.Objects::nonNull)).orElse(null);
        return new SubjectSummary(subject, grades, average, grades.get(AcademicPeriod.FINAL));
    }

    /**
     * The report card of one student for one year. Subject average = mean of the periods I-IV
     * graded; general average of a period = mean of that period across all the subjects; yearly
     * average = mean of the subject averages.
     */
    public static YearReport yearReport(int year, List<AcademicRecord> yearRecords) {
        List<SubjectSummary> subjects = yearRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getSubject().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(list -> summary(list.get(0).getSubject(), list))
                .sorted(Comparator.comparing(s -> s.subject().getName()))
                .toList();

        Map<AcademicPeriod, BigDecimal> periodAverages = new EnumMap<>(AcademicPeriod.class);
        for (AcademicPeriod period : AcademicPeriod.values()) {
            average(yearRecords.stream().filter(r -> r.getPeriod() == period).map(AcademicRecord::getGrade))
                    .ifPresent(value -> periodAverages.put(period, value));
        }
        BigDecimal yearAverage = average(subjects.stream().map(SubjectSummary::average).filter(java.util.Objects::nonNull))
                .orElse(null);
        String gradeName = yearRecords.isEmpty() ? "" : yearRecords.get(0).getSubject().getGradeLevel().getName();
        return new YearReport(year, gradeName, subjects, periodAverages, yearAverage);
    }

    /** Every year of a student, oldest first: its academic history. */
    public static List<YearReport> history(List<AcademicRecord> allRecords) {
        return allRecords.stream()
                .collect(Collectors.groupingBy(AcademicRecord::getAcademicYear, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> yearReport(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Average, lowest and highest grade of every subject in one period, for a whole course. */
    public static List<CourseSubjectAverage> courseAverages(List<AcademicRecord> courseRecords, AcademicPeriod period) {
        return courseRecords.stream()
                .filter(r -> r.getPeriod() == period)
                .collect(Collectors.groupingBy(r -> r.getSubject().getId()))
                .values().stream()
                .map(list -> new CourseSubjectAverage(
                        list.get(0).getSubject(),
                        average(list.stream().map(AcademicRecord::getGrade)).orElseThrow(),
                        list.stream().map(AcademicRecord::getGrade).min(Comparator.naturalOrder()).orElseThrow(),
                        list.stream().map(AcademicRecord::getGrade).max(Comparator.naturalOrder()).orElseThrow(),
                        list.size()))
                .sorted(Comparator.comparing(c -> c.subject().getName()))
                .toList();
    }
}
