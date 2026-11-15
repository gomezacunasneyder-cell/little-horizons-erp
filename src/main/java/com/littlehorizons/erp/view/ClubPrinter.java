package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.club.ClubCatalog;
import com.littlehorizons.erp.controller.club.ClubOccupancy;
import com.littlehorizons.erp.controller.club.ClubSummary;
import com.littlehorizons.erp.model.entity.Club;
import com.littlehorizons.erp.model.entity.ClubMembership;

import java.util.List;

/** Prints clubs and memberships as tables. It only formats; every number comes already calculated. */
final class ClubPrinter {

    private final ConsoleOutput output;

    ClubPrinter(ConsoleOutput output) {
        this.output = output;
    }

    void catalog(ClubCatalog catalog) {
        output.table(List.of("Club", "Profesor encargado", "Horario", "Cupo", "Inscritos", "Libres", "Estado"),
                catalog.clubs().stream().map(this::catalogRow).toList());
        ClubSummary summary = catalog.summary();
        output.info("Clubes: " + summary.totalClubs() + " (" + summary.activeClubs() + " abiertos, "
                + summary.fullClubs() + " llenos). Miembros activos: " + summary.activeMemberships()
                + ". Cupos libres en clubes abiertos: " + summary.availableSpots() + ".");
    }

    private List<String> catalogRow(ClubOccupancy o) {
        Club club = o.club();
        String state = !club.isActive() ? "Cerrado" : o.isFull() ? "Lleno" : "Abierto";
        return List.of(club.getName(), club.getSupervisor().getFullName(), club.getMeetingSchedule(),
                String.valueOf(club.getMaxCapacity()), String.valueOf(o.activeMembers()),
                String.valueOf(o.availableSpots()), state);
    }

    /** The members of one club: who, since when, and if they left, when and why. */
    void members(Club club, List<ClubMembership> memberships) {
        output.info("«" + club.getName() + "» - " + club.getDescription());
        output.info("Profesor encargado: " + club.getSupervisor().getFullName() + " - Horario: " + club.getMeetingSchedule()
                + " - Estado: " + (club.isActive() ? "abierto" : "cerrado"));
        output.table(List.of("Estudiante", "Documento", "Ingreso", "Estado", "Retiro", "Motivo"),
                memberships.stream().map(m -> List.of(m.getStudent().getFullName(), m.getStudent().getDocumentNumber(),
                        m.getJoinDate().toString(), m.getStatus().getDisplayName(), text(m.getWithdrawalDate()),
                        text(m.getWithdrawalReason()))).toList());
        long active = memberships.stream().filter(ClubMembership::isActive).count();
        output.info(active + " de " + club.getMaxCapacity() + " cupos ocupados; "
                + (memberships.size() - active) + " retiro(s) en el historial.");
    }

    /** The clubs one student has belonged to. */
    void studentClubs(List<ClubMembership> memberships) {
        output.table(List.of("Club", "Ingreso", "Estado", "Retiro", "Motivo"),
                memberships.stream().map(m -> List.of(m.getClub().getName(), m.getJoinDate().toString(),
                        m.getStatus().getDisplayName(), text(m.getWithdrawalDate()), text(m.getWithdrawalReason()))).toList());
    }

    /** The data of one club, shown before editing it. */
    void detail(Club club) {
        output.line("  Nombre:      " + club.getName());
        output.line("  Descripción: " + club.getDescription());
        output.line("  Profesor:    " + club.getSupervisor().getFullName());
        output.line("  Cupo máximo: " + club.getMaxCapacity());
        output.line("  Horario:     " + club.getMeetingSchedule());
    }

    private static String text(Object value) {
        return value == null ? "-" : value.toString();
    }
}
