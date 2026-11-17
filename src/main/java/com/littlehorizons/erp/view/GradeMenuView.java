package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.AssignmentController;
import com.littlehorizons.erp.controller.GradeController;
import com.littlehorizons.erp.controller.GradeReportController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.RosterEntry;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.grading.CourseSubjectAverage;
import com.littlehorizons.erp.controller.grading.SubjectSummary;
import com.littlehorizons.erp.controller.grading.YearReport;
import com.littlehorizons.erp.model.entity.AcademicRecord;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.AcademicPeriod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Registering grades (one by one or by group), corrections, final grades, report cards and averages. */
public final class GradeMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Registrar una nota",
            "Registrar notas de un grupo (materia y período)",
            "Corregir una nota",
            "Calcular la nota final de una materia",
            "Boletín de un estudiante en un año",
            "Historial académico de un estudiante",
            "Promedios de un curso por materia");

    private static final List<AcademicPeriod> ALL_PERIODS = List.of(AcademicPeriod.values());

    private final GradeController grades;
    private final GradeReportController reports;
    private final AssignmentController assignments;
    private final EntitySelector<Teacher> teacherSelector;
    private final StudentSelector studentSelector;
    private final ResultPrinter printer;

    public GradeMenuView(ConsoleInput input, ConsoleOutput output, GradeController grades,
                         GradeReportController reports, AssignmentController assignments,
                         TeacherController teachers, StudentController students) {
        super(input, output);
        this.grades = grades;
        this.reports = reports;
        this.assignments = assignments;
        this.teacherSelector = TeacherMenuView.teacherSelector(input, output, teachers);
        this.studentSelector = new StudentSelector(input, output, students);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "CALIFICACIONES Y PROMEDIOS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> registerOne();
            case 2 -> registerGroup();
            case 3 -> correct();
            case 4 -> calculateFinal();
            case 5 -> showYearReport();
            case 6 -> showHistory();
            case 7 -> showCourseAverages();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    // ---- registering ----

    private void registerOne() {
        Teacher teacher = teacherSelector.select();
        int year = input.readAcademicYear();
        TeachingAssignment assignment = chooseAssignment(teacher, year);
        if (assignment == null) {
            return;
        }
        Student student = studentSelector.select();
        AcademicPeriod period = readPeriod();
        BigDecimal grade = input.readGrade("Nota (0.0 a 5.0): ");
        String observations = input.readOptionalText("Observaciones (Enter para omitir): ", AcademicRecord.OBSERVATIONS_MAX);
        printer.report(grades.record(teacher, student, assignment.getSubject(), year, period, grade, observations));
    }

    private void registerGroup() {
        Teacher teacher = teacherSelector.select();
        int year = input.readAcademicYear();
        TeachingAssignment assignment = chooseAssignment(teacher, year);
        if (assignment == null) {
            return;
        }
        AcademicPeriod period = readPeriod();
        Result<List<RosterEntry>> roster = grades.roster(assignment, period);
        if (!roster.isSuccess()) {
            output.error(roster.getMessage());
            return;
        }
        if (roster.getData().isEmpty()) {
            output.info("No hay estudiantes inscritos en " + assignment.getSubject().getName() + " para ese grupo y período.");
            return;
        }
        output.info(assignment + " - " + period.getDisplayName() + ". Enter = omitir al estudiante; «cancelar» = terminar.");
        int saved = 0;
        for (RosterEntry entry : roster.getData()) {
            if (entry.existing() != null) {
                output.line(entry.student().getFullName() + ": ya tiene " + entry.existing().getGrade() + " (use «Corregir una nota»).");
                continue;
            }
            BigDecimal grade = input.readOptionalGrade(entry.student().getFullName() + " (0.0 a 5.0): ");
            if (grade == null) {
                continue;
            }
            Result<AcademicRecord> result = grades.record(teacher, entry.student(), assignment.getSubject(),
                    year, period, grade, null);
            printer.report(result);
            if (result.isSuccess()) {
                saved++;
            }
        }
        output.info("Notas registradas en esta sesión: " + saved + ".");
    }

    private void correct() {
        Teacher teacher = teacherSelector.select();
        int year = input.readAcademicYear();
        Student student = studentSelector.select();
        Result<List<AcademicRecord>> records = grades.correctableRecords(teacher, student, year);
        if (!records.isSuccess()) {
            output.error(records.getMessage());
            return;
        }
        if (records.getData().isEmpty()) {
            output.info(student.getFullName() + " no tiene notas de sus materias en " + year + ".");
            return;
        }
        AcademicRecord record = input.readOption("Nota a corregir: ", records.getData(),
                r -> r.getSubject().getName() + " - " + r.getPeriod().getDisplayName() + ": " + r.getGrade());
        BigDecimal grade = input.readGrade("Nueva nota (actual " + record.getGrade() + "): ");
        String observations = input.readOptionalText("Observaciones (Enter para omitir): ", AcademicRecord.OBSERVATIONS_MAX);
        if (input.readYesNo("¿Cambiar " + record.getGrade() + " por " + grade + "?")) {
            printer.report(grades.correct(teacher, record, grade, observations));
        } else {
            output.info("No se realizaron cambios.");
        }
    }

    private void calculateFinal() {
        Teacher teacher = teacherSelector.select();
        int year = input.readAcademicYear();
        TeachingAssignment assignment = chooseAssignment(teacher, year);
        if (assignment == null) {
            return;
        }
        Student student = studentSelector.select();
        printer.report(grades.generateFinal(teacher, student, assignment.getSubject(), year));
    }

    // ---- consulting ----

    private void showYearReport() {
        Student student = studentSelector.select();
        int year = input.readAcademicYear();
        Result<YearReport> result = reports.yearReport(student, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info("Boletín de " + student.getFullName());
        printYearReport(result.getData());
    }

    private void showHistory() {
        Student student = studentSelector.select();
        Result<List<YearReport>> result = reports.history(student);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info("Historial académico de " + student.getFullName());
        result.getData().forEach(this::printYearReport);
    }

    void showCourseAverages() {
        Result<List<GradeLevel>> levels = assignments.grades();
        if (!levels.isSuccess()) {
            output.error(levels.getMessage());
            return;
        }
        GradeLevel grade = input.readOption("Grado: ", levels.getData(), GradeLevel::getName);
        int year = input.readAcademicYear();
        AcademicPeriod period = readPeriod();
        Result<List<CourseSubjectAverage>> result = reports.courseAverages(grade, year, period);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info("Promedios de " + grade.getName() + " - " + period.getDisplayName() + " de " + year);
        output.table(List.of("Materia", "Promedio", "Más baja", "Más alta", "Estudiantes"),
                result.getData().stream()
                        .map(c -> List.of(c.subject().getName(), c.average().toPlainString(),
                                c.lowest().toPlainString(), c.highest().toPlainString(), String.valueOf(c.students())))
                        .toList());
    }

    // ---- helpers ----

    private void printYearReport(YearReport report) {
        output.title(report.year() + " - " + report.gradeName());
        List<List<String>> rows = new ArrayList<>(report.subjects().stream()
                .map(this::row)
                .toList());
        List<String> general = new ArrayList<>();
        general.add("PROMEDIO DEL PERÍODO");
        AcademicPeriod.teachingPeriods().forEach(p -> general.add(show(report.periodAverages().get(p))));
        general.add(show(report.yearAverage()));
        general.add(show(report.periodAverages().get(AcademicPeriod.FINAL)));
        rows.add(general);
        output.table(List.of("Materia", "I", "II", "III", "IV", "Prom. I-IV", "Final"), rows);
    }

    private List<String> row(SubjectSummary summary) {
        List<String> cells = new ArrayList<>();
        cells.add(summary.subject().getName());
        AcademicPeriod.teachingPeriods().forEach(p -> cells.add(show(summary.periodGrades().get(p))));
        cells.add(show(summary.average()));
        cells.add(show(summary.finalGrade()));
        return cells;
    }

    private static String show(BigDecimal value) {
        return value == null ? "-" : value.toPlainString();
    }

    private AcademicPeriod readPeriod() {
        return input.readOption("Período: ", ALL_PERIODS, AcademicPeriod::getDisplayName);
    }

    /** The subjects the teacher teaches in the year; null (with a message) if he has none. */
    private TeachingAssignment chooseAssignment(Teacher teacher, int year) {
        Result<List<TeachingAssignment>> result = assignments.byTeacher(teacher, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return null;
        }
        if (result.getData().isEmpty()) {
            output.info(teacher.getFullName() + " no tiene materias asignadas en " + year + ".");
            return null;
        }
        return input.readOption("Materia y grupo: ", result.getData(), TeachingAssignment::toString);
    }
}
