package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.SubjectRegistrationController;
import com.littlehorizons.erp.model.entity.Enrollment;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.Subject;
import com.littlehorizons.erp.model.entity.SubjectRegistration;
import com.littlehorizons.erp.model.enums.AcademicPeriod;
import com.littlehorizons.erp.model.enums.RegistrationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/** Registration of students in the subjects of their grade, period by period. */
public final class SubjectRegistrationMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Inscribir en una materia",
            "Inscribir en todas las materias de su grado",
            "Ver inscripciones de un estudiante",
            "Retirar una inscripción");

    private static final Pattern SECTION = Pattern.compile("[A-Za-z0-9]{1,10}");

    private final SubjectRegistrationController controller;
    private final StudentSelector selector;
    private final StudentPrinter printer;

    public SubjectRegistrationMenuView(ConsoleInput input, ConsoleOutput output,
                                       SubjectRegistrationController controller, StudentController studentController) {
        super(input, output);
        this.controller = controller;
        this.selector = new StudentSelector(input, output, studentController);
        this.printer = new StudentPrinter(output);
    }

    @Override
    protected String title() {
        return "INSCRIPCIÓN EN MATERIAS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> registerOne();
            case 2 -> registerAll();
            case 3 -> showRegistrations();
            case 4 -> withdraw();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private int readYear() {
        return input.readInt("Año académico: ", Enrollment.MIN_YEAR, LocalDate.now().getYear() + 1);
    }

    private AcademicPeriod readPeriod() {
        return input.readOption("Período: ", AcademicPeriod.teachingPeriods(), p -> "Período " + p.getDbLabel());
    }

    private void registerOne() {
        Student student = selector.select();
        int year = readYear();
        Result<List<Subject>> subjects = controller.availableSubjects(student, year);
        if (!subjects.isSuccess()) {
            output.error(subjects.getMessage());
            return;
        }
        if (subjects.getData().isEmpty()) {
            output.info("Su grado no tiene materias activas registradas.");
            return;
        }
        Subject subject = input.readOption("Materia: ", subjects.getData(),
                s -> s + " (" + s.getWeeklyHours() + " h/sem)");
        AcademicPeriod period = readPeriod();
        String section = input.readOptionalMatching("Sección (Enter para omitir): ", SECTION, "Use hasta 10 letras o números.");
        printer.report(controller.register(student, subject, year, period, section));
    }

    private void registerAll() {
        Student student = selector.select();
        int year = readYear();
        AcademicPeriod period = readPeriod();
        printer.report(controller.registerAll(student, year, period));
    }

    private void showRegistrations() {
        Student student = selector.select();
        int year = readYear();
        Result<List<SubjectRegistration>> result = controller.byStudent(student, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        output.info("Inscripciones de " + student.getFullName() + " en " + year);
        output.table(List.of("Período", "Materia", "Sección", "Estado"),
                result.getData().stream()
                        .map(r -> List.of(r.getPeriod().getDbLabel(), r.getSubject().toString(),
                                r.getSection() == null ? "-" : r.getSection(), r.getStatus().getDisplayName()))
                        .toList());
    }

    private void withdraw() {
        Student student = selector.select();
        int year = readYear();
        Result<List<SubjectRegistration>> result = controller.byStudent(student, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
            return;
        }
        List<SubjectRegistration> active = result.getData().stream()
                .filter(r -> r.getStatus() == RegistrationStatus.ACTIVE).toList();
        if (active.isEmpty()) {
            output.info("No hay inscripciones activas en " + year + ".");
            return;
        }
        SubjectRegistration registration = input.readOption("Inscripción a retirar: ", active,
                r -> "Período " + r.getPeriod().getDbLabel() + " - " + r.getSubject());
        printer.report(controller.withdraw(registration));
    }
}
