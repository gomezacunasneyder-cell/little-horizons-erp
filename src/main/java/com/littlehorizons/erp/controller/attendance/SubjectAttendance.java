package com.littlehorizons.erp.controller.attendance;

import com.littlehorizons.erp.model.entity.Subject;

/** Attendance of one student in one subject. */
public record SubjectAttendance(Subject subject, AttendanceCounts counts) {
}
