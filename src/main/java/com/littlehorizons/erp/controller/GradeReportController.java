package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.grading.CourseSubjectAverage;
import com.littlehorizons.erp.controller.grading.GradeAverages;
import com.littlehorizons.erp.controller.grading.YearReport;
import com.littlehorizons.erp.model.dao.AcademicRecordDao;
import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.util.ErrorLogger;

import java.util.List;

/**
 * Read-only side of the grades: report card of a year, academic history and course averages.
 * It does not change anything, so it was separated from GradeController (which registers and
 * corrects grades). The calculations themselves are in GradeAverages.
 */
public class GradeReportController extends BaseController {

    private final AcademicRecordDao recordDao;

    public GradeReportController(AcademicRecordDao recordDao, ErrorLogger logger) {
        super(logger);
        this.recordDao = recordDao;
    }

    public Result<YearReport> yearReport(Student student, int academicYear) {
        return execute("Year report", () -> {
            List<AcademicRecord> records = recordDao.findByStudentAndYear(student.getId(), academicYear);
            if (records.isEmpty()) {
                return Result.fail(student.getFullName() + " no tiene notas registradas en " + academicYear + ".");
            }
            return Result.ok(GradeAverages.yearReport(academicYear, records), "");
        });
    }

    public Result<List<YearReport>> history(Student student) {
        return execute("Academic history", () -> {
            List<AcademicRecord> records = recordDao.findByStudent(student.getId());
            if (records.isEmpty()) {
                return Result.fail(student.getFullName() + " no tiene notas registradas.");
            }
            return Result.ok(GradeAverages.history(records), "");
        });
    }

    public Result<List<CourseSubjectAverage>> courseAverages(GradeLevel grade, int academicYear, AcademicPeriod period) {
        return execute("Course averages", () -> {
            List<CourseSubjectAverage> averages = GradeAverages.courseAverages(
                    recordDao.findByGradeAndYear(grade.getId(), academicYear), period);
            if (averages.isEmpty()) {
                return Result.fail("No hay notas de " + grade.getName() + " en " + period.getDisplayName() + " de " + academicYear + ".");
            }
            return Result.ok(averages, "");
        });
    }
}
