package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.entity.Student;

/** A student of a group with the attendance already registered that day (null if none yet). */
public record AttendanceRosterEntry(Student student, AttendanceRecord existing) {
}
