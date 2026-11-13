package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.EnrollmentController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.GradeLevel;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;

import java.time.LocalDate;
import java.util.List;

/** Enrollment of students per grade and academic year. */
public final class EnrollmentMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Matricular estudiante",
            "Ver matrículas de un estudiante",
            "Listado de un grado en un año",
            "Cerrar matrícula (completar o retirar)");

    private final EnrollmentController controller;
    private final StudentSelector selector;
    private final StudentPrinter printer;

    public EnrollmentMenuView(ConsoleInput input, ConsoleOutput output,
                              EnrollmentController controller, StudentController studentController) {
        super(input, output);
        this.controller = controller;
        this.selector = new StudentSelector(input, output, studentController);
        this.printer = new StudentPrinter(output);
    }

    @Override
    protected String title() {
        return "MATRÍCULAS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> enroll();
            case 2 -> byStudent();
            case 3 -> byGrade();
            case 4 -> close();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private int readYear() {
        return input.readInt("Año académico: ", Enrollment.MIN_YEAR, LocalDate.now().getYear() + 1);
    }

    private GradeLevel readGrade() {
        Result<List<GradeLevel>> grades = controller.grades();
        if (!grades.isSuccess()) {
            output.error(grades.getMessage());
            return null;
        }
        return input.readOption("Grado: ", grades.getData(), GradeLevel::getName);
    }

    private void enroll() {
        Student student = selector.select();
        GradeLevel grade = readGrade();
        if (grade != null) {
            printer.report(controller.enroll(student, grade, readYear()));
        }
    }

    private void byStudent() {
        Student student = selector.select();
        Result<List<Enrollment>> result = controller.byStudent(student);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.table(List.of("Año", "Grado", "Estado", "Fecha matrícula", "Fecha retiro"),
                result.getData().stream()
                        .map(e -> List.of(String.valueOf(e.getAcademicYear()), e.getGradeLevel().getName(),
                                e.getStatus().getDisplayName(), e.getEnrollmentDate().toString(),
                                e.getWithdrawalDate() == null ? "-" : e.getWithdrawalDate().toString()))
                        .toList());
    }

    private void byGrade() {
        GradeLevel grade = readGrade();
        if (grade == null) {
            return;
        }
        int year = readYear();
        Result<List<Enrollment>> result = controller.byGradeAndYear(grade, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info(grade.getName() + " - " + year + ": " + result.getData().size() + " matrícula(s)");
        output.table(List.of("Código", "Estudiante", "Estado"),
                result.getData().stream()
                        .map(e -> List.of(String.valueOf(e.getStudent().getId()), e.getStudent().getFullName(),
                                e.getStatus().getDisplayName()))
                        .toList());
    }

    private void close() {
        Student student = selector.select();
        Result<List<Enrollment>> result = controller.byStudent(student);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        List<Enrollment> active = result.getData().stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).toList();
        if (active.isEmpty()) {
            output.info("El estudiante no tiene matrículas activas.");
            return;
        }
        Enrollment enrollment = input.readOption("Matrícula a cerrar: ", active,
                e -> e.getGradeLevel().getName() + " " + e.getAcademicYear());
        EnrollmentStatus newStatus = input.readOption("Cerrar como: ",
                List.of(EnrollmentStatus.COMPLETED, EnrollmentStatus.WITHDRAWN), EnrollmentStatus::getDisplayName);
        printer.report(controller.close(enrollment, newStatus));
    }
}
