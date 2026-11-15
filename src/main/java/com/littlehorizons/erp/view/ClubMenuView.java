package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.ClubController;
import com.littlehorizons.erp.controller.ClubMembershipController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.club.ClubCatalog;
import com.littlehorizons.erp.model.entity.Club;
import com.littlehorizons.erp.model.entity.ClubMembership;
import com.littlehorizons.erp.model.entity.Student;

import java.time.LocalDate;
import java.util.List;

/**
 * "Actividades Extracurriculares - Clubes": the catalog with its capacity, creating and editing
 * clubs, enrolling and withdrawing students, and the follow-up of who belongs to what. It only
 * asks for data and prints; the rules (capacity, active students, unique names) are in the controllers.
 */
public final class ClubMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Ver el catálogo de clubes (cupos y disponibilidad)",
            "Crear un club",
            "Editar un club",
            "Cerrar o reabrir un club",
            "Inscribir un estudiante en un club",
            "Retirar un estudiante de un club",
            "Ver los miembros de un club",
            "Ver los clubes de un estudiante");

    private final ClubController clubs;
    private final ClubMembershipController memberships;
    private final EntitySelector<Club> clubSelector;
    private final EntitySelector<Club> openClubSelector;
    private final StudentSelector studentSelector;
    private final ClubForm form;
    private final ClubPrinter clubPrinter;
    private final ResultPrinter printer;

    public ClubMenuView(ConsoleInput input, ConsoleOutput output, ClubController clubs,
                        ClubMembershipController memberships, TeacherController teachers, StudentController students) {
        super(input, output);
        this.clubs = clubs;
        this.memberships = memberships;
        this.clubSelector = clubSelector(input, output, "Nombre del club (o parte de él): ", clubs::search);
        this.openClubSelector = clubSelector(input, output, "Nombre del club al que se inscribe: ", clubs::searchActive);
        this.studentSelector = new StudentSelector(input, output, students);
        this.form = new ClubForm(input, output, clubs, TeacherMenuView.teacherSelector(input, output, teachers));
        this.clubPrinter = new ClubPrinter(output);
        this.printer = new ResultPrinter(output);
    }

    private static EntitySelector<Club> clubSelector(ConsoleInput input, ConsoleOutput output, String prompt,
                                                     java.util.function.Function<String, Result<List<Club>>> search) {
        return new EntitySelector<>(input, output, prompt, search,
                club -> club.getName() + " - " + club.getSupervisor().getFullName() + (club.isActive() ? "" : " (cerrado)"));
    }

    @Override
    protected String title() {
        return "ACTIVIDADES EXTRACURRICULARES - CLUBES";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> showCatalog();
            case 2 -> createClub();
            case 3 -> editClub();
            case 4 -> toggleClub();
            case 5 -> enroll();
            case 6 -> withdraw();
            case 7 -> showMembers();
            case 8 -> showStudentClubs();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void showCatalog() {
        Result<ClubCatalog> result = clubs.catalog();
        if (result.isSuccess()) {
            clubPrinter.catalog(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void createClub() {
        printer.report(clubs.create(form.readNew()));
    }

    private void editClub() {
        Club club = clubSelector.select();
        output.info("Datos actuales:");
        clubPrinter.detail(club);
        printer.report(clubs.update(form.readChanges(club)));
    }

    private void toggleClub() {
        Club club = clubSelector.select();
        if (club.isActive()) {
            if (input.readYesNo("¿Cerrar el club «" + club.getName() + "»?")) {
                printer.report(clubs.setActive(club, false));
            }
        } else if (input.readYesNo("El club está cerrado. ¿Reabrirlo?")) {
            printer.report(clubs.setActive(club, true));
        }
    }

    private void enroll() {
        Student student = studentSelector.select();
        Club club = openClubSelector.select();
        LocalDate date = input.readDateOrDefault("Fecha de ingreso (aaaa-mm-dd, Enter = hoy): ", LocalDate.now());
        printer.report(memberships.enroll(student, club, date));
    }

    private void withdraw() {
        Student student = studentSelector.select();
        Result<List<ClubMembership>> active = memberships.activeByStudent(student);
        if (!active.isSuccess()) {
            output.error(active.getMessage());
            return;
        }
        if (active.getData().isEmpty()) {
            output.info(student.getFullName() + " no está activo en ningún club.");
            return;
        }
        ClubMembership membership = input.readOption("Club del que se retira: ", active.getData(),
                m -> m.getClub().getName() + " (desde " + m.getJoinDate() + ")");
        LocalDate date = input.readDateOrDefault("Fecha de retiro (aaaa-mm-dd, Enter = hoy): ", LocalDate.now());
        String reason = input.readOptionalText("Motivo del retiro (Enter para omitir): ", ClubMembership.REASON_MAX);
        if (input.readYesNo("¿Retirar a " + student.getFullName() + " de «" + membership.getClub().getName() + "»?")) {
            printer.report(memberships.withdraw(membership, date, reason));
        } else {
            output.info("No se retiró a nadie.");
        }
    }

    private void showMembers() {
        Club club = clubSelector.select();
        Result<List<ClubMembership>> result = memberships.byClub(club);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
        } else {
            clubPrinter.members(club, result.getData());
        }
    }

    private void showStudentClubs() {
        Student student = studentSelector.select();
        Result<List<ClubMembership>> result = memberships.byStudent(student);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
        } else if (result.getData().isEmpty()) {
            output.info(student.getFullName() + " no ha estado en ningún club.");
        } else {
            output.info("Clubes de " + student.getFullName());
            clubPrinter.studentClubs(result.getData());
        }
    }
}
