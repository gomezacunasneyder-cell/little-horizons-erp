package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.AssignmentController;
import com.littlehorizons.erp.controller.AttendanceController;
import com.littlehorizons.erp.controller.AttendanceReportController;
import com.littlehorizons.erp.controller.AttendanceRosterEntry;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.attendance.AbsenceAlert;
import com.littlehorizons.erp.controller.attendance.AbsenceReport;
import com.littlehorizons.erp.controller.attendance.AttendanceCounts;
import com.littlehorizons.erp.controller.attendance.AttendanceSummary;
import com.littlehorizons.erp.model.entity.AttendanceRecord;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.model.entity.TeachingAssignment;
import com.littlehorizons.erp.model.enums.AttendanceStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Attendance screens: register a whole group or one student, correct, consult a group on a day,
 * the summary of a student and the report of concerning absence patterns. It only asks for data
 * and prints; the rules are in the controllers.
 */
public final class AttendanceMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Registrar asistencia de un grupo (materia y fecha)",
            "Registrar asistencia de un estudiante",
            "Corregir una asistencia",
            "Ver la asistencia de un grupo en una fecha",
            "Resumen de asistencia de un estudiante",
            "Reporte de patrones de inasistencia");

    private static final String STATUS_LEGEND = "1 = Presente (Enter), 2 = Ausente, 3 = Tardanza, 4 = Excusa Médica";

    private final AttendanceController attendance;
    private final AttendanceReportController reports;
    private final AssignmentController assignments;
    private final EntitySelector<Teacher> teacherSelector;
    private final StudentSelector studentSelector;
    private final ResultPrinter printer;

    public AttendanceMenuView(ConsoleInput input, ConsoleOutput output, AttendanceController attendance,
                              AttendanceReportController reports, AssignmentController assignments,
                              TeacherController teachers, StudentController students) {
        super(input, output);
        this.attendance = attendance;
        this.reports = reports;
        this.assignments = assignments;
        this.teacherSelector = TeacherMenuView.teacherSelector(input, output, teachers);
        this.studentSelector = new StudentSelector(input, output, students);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "CONTROL DE ASISTENCIA";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> registerGroup();
            case 2 -> registerOne();
            case 3 -> correct();
            case 4 -> showGroup();
            case 5 -> showSummary();
            case 6 -> showAbsenceReport();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    // ---- registering ----

    private void registerGroup() {
        Teacher teacher = teacherSelector.select();
        LocalDate date = input.readSchoolDate("Fecha de la clase (aaaa-mm-dd, Enter = hoy): ");
        TeachingAssignment assignment = chooseAssignment(teacher, date.getYear());
        if (assignment == null) {
            return;
        }
        Result<List<AttendanceRosterEntry>> roster = attendance.roster(assignment, date);
        if (!roster.isSuccess()) {
            output.error(roster.getMessage());
            return;
        }
        if (roster.getData().isEmpty()) {
            output.info("No hay estudiantes inscritos en " + assignment.getSubject().getName() + " para ese grupo y fecha.");
            return;
        }
        output.info(assignment + " - " + date + ". «cancelar» = terminar.");
        output.line(STATUS_LEGEND);
        int saved = 0;
        for (AttendanceRosterEntry entry : roster.getData()) {
            if (entry.existing() != null) {
                output.line(entry.student().getFullName() + ": ya tiene " + entry.existing().getStatus().getDisplayName()
                        + " (use «Corregir una asistencia»).");
                continue;
            }
            AttendanceStatus status = input.readAttendanceStatus(entry.student().getFullName() + ": ");
            Result<AttendanceRecord> result = attendance.record(teacher, entry.student(), assignment.getSubject(),
                    date, status, null);
            if (result.isSuccess()) {
                saved++;
            } else {
                output.error(result.getMessage());
            }
        }
        output.info("Asistencias registradas en esta sesión: " + saved + ".");
    }

    private void registerOne() {
        Teacher teacher = teacherSelector.select();
        LocalDate date = input.readSchoolDate("Fecha de la clase (aaaa-mm-dd, Enter = hoy): ");
        TeachingAssignment assignment = chooseAssignment(teacher, date.getYear());
        if (assignment == null) {
            return;
        }
        Student student = studentSelector.select();
        output.line(STATUS_LEGEND);
        AttendanceStatus status = input.readAttendanceStatus("Estado: ");
        String observations = input.readOptionalText("Observaciones (Enter para omitir): ", AttendanceRecord.OBSERVATIONS_MAX);
        printer.report(attendance.record(teacher, student, assignment.getSubject(), date, status, observations));
    }

    private void correct() {
        Teacher teacher = teacherSelector.select();
        Student student = studentSelector.select();
        LocalDate date = input.readSchoolDate("Fecha de la clase (aaaa-mm-dd, Enter = hoy): ");
        Result<List<AttendanceRecord>> records = attendance.correctableRecords(teacher, student, date);
        if (!records.isSuccess()) {
            output.error(records.getMessage());
            return;
        }
        if (records.getData().isEmpty()) {
            output.info(student.getFullName() + " no tiene asistencia registrada el " + date + " en materias de este profesor.");
            return;
        }
        AttendanceRecord record = input.readOption("Asistencia a corregir: ", records.getData(),
                r -> r.getSubject().getName() + ": " + r.getStatus().getDisplayName());
        output.line(STATUS_LEGEND);
        AttendanceStatus status = input.readAttendanceStatus("Nuevo estado (actual " + record.getStatus().getDisplayName() + "): ");
        String observations = input.readOptionalText("Observaciones (Enter para omitir): ", AttendanceRecord.OBSERVATIONS_MAX);
        if (input.readYesNo("¿Cambiar " + record.getStatus().getDisplayName() + " por " + status.getDisplayName() + "?")) {
            printer.report(attendance.correct(teacher, record, status, observations));
        } else {
            output.info("No se realizaron cambios.");
        }
    }

    // ---- consulting ----

    private void showGroup() {
        Teacher teacher = teacherSelector.select();
        LocalDate date = input.readSchoolDate("Fecha de la clase (aaaa-mm-dd, Enter = hoy): ");
        TeachingAssignment assignment = chooseAssignment(teacher, date.getYear());
        if (assignment == null) {
            return;
        }
        Result<List<AttendanceRosterEntry>> roster = attendance.roster(assignment, date);
        if (!roster.isSuccess()) {
            output.error(roster.getMessage());
            return;
        }
        if (roster.getData().isEmpty()) {
            output.info("No hay estudiantes inscritos en ese grupo en esa fecha.");
            return;
        }
        output.info(assignment + " - " + date);
        output.table(List.of("Estudiante", "Estado", "Observaciones"),
                roster.getData().stream()
                        .map(e -> List.of(e.student().getFullName(),
                                e.existing() == null ? "Sin registrar" : e.existing().getStatus().getDisplayName(),
                                e.existing() == null || e.existing().getObservations() == null ? "-" : e.existing().getObservations()))
                        .toList());
    }

    private void showSummary() {
        Student student = studentSelector.select();
        int year = input.readAcademicYear();
        Result<AttendanceSummary> result = reports.summary(student, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info("Asistencia de " + student.getFullName() + " en " + year);
        List<List<String>> rows = new ArrayList<>(result.getData().subjects().stream()
                .map(s -> row(s.subject().getName(), s.counts()))
                .toList());
        rows.add(row("TOTAL", result.getData().overall()));
        output.table(List.of("Materia", "Presente", "Ausente", "Tardanza", "Excusa", "Total", "% asistencia"), rows);
        output.line("% asistencia = (presentes + tardanzas) / clases, sin contar las excusas médicas.");
    }

    void showAbsenceReport() {
        LocalDate defaultFrom = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate from = input.readDateOrDefault("Desde (aaaa-mm-dd, Enter = " + defaultFrom + "): ", defaultFrom);
        LocalDate to = input.readDateOrDefault("Hasta (aaaa-mm-dd, Enter = hoy): ", LocalDate.now());
        Result<AbsenceReport> result = reports.concerningAbsences(from, to);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        AbsenceReport report = result.getData();
        output.info("Estudiantes con más de " + report.weeklyLimit() + " ausencias sin excusa en una semana ("
                + report.from() + " a " + report.to() + ")");
        if (report.alerts().isEmpty()) {
            output.info("No hay estudiantes con ese patrón en el rango indicado.");
            return;
        }
        output.table(List.of("Estudiante", "Grado", "Semana del", "Ausencias", "Materias"),
                report.alerts().stream()
                        .map(a -> List.of(a.student().getFullName(), a.gradeName(), a.weekStart().toString(),
                                String.valueOf(a.count()), a.subjectNames().stream().collect(Collectors.joining(", "))))
                        .toList());
        output.line("Total de casos: " + report.alerts().size() + " (" + distinctStudents(report) + " estudiante(s)).");
    }

    // ---- helpers ----

    private static long distinctStudents(AbsenceReport report) {
        return report.alerts().stream().map(AbsenceAlert::student).map(Student::getId).distinct().count();
    }

    private static List<String> row(String name, AttendanceCounts counts) {
        String rate = counts.ratePercent() == null ? "-" : counts.ratePercent().toPlainString() + "%";
        return List.of(name, String.valueOf(counts.present()), String.valueOf(counts.absent()),
                String.valueOf(counts.late()), String.valueOf(counts.excused()), String.valueOf(counts.total()), rate);
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
