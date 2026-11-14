package com.littlehorizons.erp.controller.attendance;

import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.entity.Student;

import java.time.LocalDate;
import java.util.List;

/** A student with a concerning number of absences in one week (the week starts on Monday). */
public record AbsenceAlert(Student student, String gradeName, LocalDate weekStart, List<AttendanceRecord> absences) {

    public int count() {
        return absences.size();
    }

    /** Names of the subjects in which the student was absent that week, without repeating. */
    public List<String> subjectNames() {
        return absences.stream().map(a -> a.getSubject().getName()).distinct().sorted().toList();
    }
}
