package com.littlehorizons.erp.controller.attendance;

import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.enums.AttendanceStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculations over attendance records, written with Streams and kept apart from the controllers
 * (they have no side effects, so they are easy to test). The statistics of one student and the
 * search for concerning weeks are here.
 */
public final class AttendanceAnalyzer {

    private AttendanceAnalyzer() {
    }

    /** Counts how many records have each state. */
    public static AttendanceCounts count(List<AttendanceRecord> records) {
        Map<AttendanceStatus, Long> byStatus = records.stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getStatus, Collectors.counting()));
        return new AttendanceCounts(
                byStatus.getOrDefault(AttendanceStatus.PRESENT, 0L).intValue(),
                byStatus.getOrDefault(AttendanceStatus.ABSENT, 0L).intValue(),
                byStatus.getOrDefault(AttendanceStatus.LATE, 0L).intValue(),
                byStatus.getOrDefault(AttendanceStatus.MEDICAL_EXCUSE, 0L).intValue());
    }

    /** One line per subject (sorted by name) and the total. */
    public static AttendanceSummary summary(List<AttendanceRecord> records) {
        List<SubjectAttendance> subjects = records.stream()
                .collect(Collectors.groupingBy(r -> r.getSubject().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(list -> {
                    Subject subject = list.get(0).getSubject();
                    return new SubjectAttendance(subject, count(list));
                })
                .sorted(Comparator.comparing(s -> s.subject().getName()))
                .toList();
        AttendanceCounts overall = subjects.stream()
                .map(SubjectAttendance::counts)
                .reduce(AttendanceCounts.EMPTY, AttendanceCounts::plus);
        return new AttendanceSummary(subjects, overall);
    }

    /** The Monday of the week of a date. */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Students with MORE than {@code weeklyLimit} unexcused absences in the same week (all their
     * subjects together). The worst weeks come first. Records that are not unexcused absences are
     * ignored, so the caller may pass any list.
     */
    public static List<AbsenceAlert> concerningWeeks(List<AttendanceRecord> records, int weeklyLimit) {
        record Key(int studentId, LocalDate weekStart) { }
        return records.stream()
                .filter(r -> r.getStatus().isUnexcusedAbsence())
                .collect(Collectors.groupingBy(r -> new Key(r.getStudent().getId(), weekStart(r.getDate()))))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > weeklyLimit)
                .map(entry -> {
                    List<AttendanceRecord> absences = entry.getValue().stream()
                            .sorted(Comparator.comparing(AttendanceRecord::getDate))
                            .toList();
                    AttendanceRecord first = absences.get(0);
                    return new AbsenceAlert(first.getStudent(), first.getSubject().getGradeLevel().getName(),
                            entry.getKey().weekStart(), absences);
                })
                .sorted(Comparator.comparingInt(AbsenceAlert::count).reversed()
                        .thenComparing(alert -> alert.student().getFullName())
                        .thenComparing(AbsenceAlert::weekStart))
                .toList();
    }
}
