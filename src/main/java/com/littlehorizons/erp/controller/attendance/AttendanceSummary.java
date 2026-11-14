package com.littlehorizons.erp.controller.attendance;

import java.util.List;

/** Attendance of one student: a line per subject and the total of all of them. */
public record AttendanceSummary(List<SubjectAttendance> subjects, AttendanceCounts overall) {
}
