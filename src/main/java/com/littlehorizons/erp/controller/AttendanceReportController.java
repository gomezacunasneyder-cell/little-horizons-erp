package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.attendance.AbsenceAlert;
import com.littlehorizons.erp.controller.attendance.AbsenceReport;
import com.littlehorizons.erp.controller.attendance.AttendanceAnalyzer;
import com.littlehorizons.erp.controller.attendance.AttendanceSummary;
import com.littlehorizons.erp.model.dao.AttendanceDao;
import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.AttendanceStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Read-only side of the attendance: the summary of a student and the report of students with a
 * concerning pattern of absences. The weekly limit comes from config.txt and is read when the
 * report is asked, so a wrong value gives a message there instead of stopping the program.
 */
public class AttendanceReportController extends BaseController {

    private static final long MAX_RANGE_DAYS = 366;

    private final AttendanceDao attendanceDao;
    private final IntSupplier weeklyAbsenceLimit;

    public AttendanceReportController(AttendanceDao attendanceDao, IntSupplier weeklyAbsenceLimit, ErrorLogger logger) {
        super(logger);
        this.attendanceDao = attendanceDao;
        this.weeklyAbsenceLimit = weeklyAbsenceLimit;
    }

    public Result<AttendanceSummary> summary(Student student, int academicYear) {
        return execute("Attendance summary", () -> {
            List<AttendanceRecord> records = attendanceDao.findByStudentBetween(student.getId(),
                    LocalDate.of(academicYear, 1, 1), LocalDate.of(academicYear, 12, 31));
            if (records.isEmpty()) {
                return Result.fail(student.getFullName() + " no tiene asistencia registrada en " + academicYear + ".");
            }
            return Result.ok(AttendanceAnalyzer.summary(records), "");
        });
    }

    /** Students with more absences in a week than the configured limit, between two dates. */
    public Result<AbsenceReport> concerningAbsences(LocalDate from, LocalDate to) {
        return execute("Absence report", () -> {
            if (from.isAfter(to)) {
                return Result.fail("La fecha inicial no puede ser posterior a la final.");
            }
            if (to.toEpochDay() - from.toEpochDay() > MAX_RANGE_DAYS) {
                return Result.fail("El rango no puede superar " + MAX_RANGE_DAYS + " días.");
            }
            int limit = weeklyAbsenceLimit.getAsInt();
            List<AbsenceAlert> alerts = AttendanceAnalyzer.concerningWeeks(
                    attendanceDao.findByStatusBetween(AttendanceStatus.ABSENT, from, to), limit);
            return Result.ok(new AbsenceReport(limit, from, to, alerts), "");
        });
    }
}
