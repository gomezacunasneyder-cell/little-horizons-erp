package com.littlehorizons.erp.controller.report;

import com.littlehorizons.erp.controller.club.ClubSummary;
import com.littlehorizons.erp.controller.event.EventSummary;
import com.littlehorizons.erp.model.enums.ResourceStatus;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.model.enums.TeacherStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * The general picture of the school as of {@code asOf}, with every figure already calculated so
 * the view only prints it. The enum maps contain EVERY state (0 when none) and the map of grades
 * keeps the grade order (preescolar to 11°).
 */
public record SchoolOverview(LocalDate asOf, int academicYear,
                             Map<StudentStatus, Long> studentsByStatus,
                             Map<String, Long> enrolledByGrade,
                             Map<TeacherStatus, Long> teachersByStatus,
                             ClubSummary clubs,
                             EventSummary events,
                             Map<ResourceStatus, Long> resourcesByStatus,
                             int openLoans, int overdueLoans,
                             int debtorStudents, BigDecimal totalOwed) {

    public long totalStudents() {
        return studentsByStatus.values().stream().mapToLong(Long::longValue).sum();
    }

    public long totalTeachers() {
        return teachersByStatus.values().stream().mapToLong(Long::longValue).sum();
    }

    public long totalResources() {
        return resourcesByStatus.values().stream().mapToLong(Long::longValue).sum();
    }

    public long totalEnrolled() {
        return enrolledByGrade.values().stream().mapToLong(Long::longValue).sum();
    }
}
