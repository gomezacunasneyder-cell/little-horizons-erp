package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.report.SchoolOverview;
import com.littlehorizons.erp.model.enums.DbEnum;
import com.littlehorizons.erp.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Prints the general overview of the school. It only formats; every figure comes already calculated. */
final class StatisticsPrinter {

    private final ConsoleOutput output;

    StatisticsPrinter(ConsoleOutput output) {
        this.output = output;
    }

    void overview(SchoolOverview overview) {
        output.info("Resumen general del colegio al " + overview.asOf() + " (año académico " + overview.academicYear() + ")");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Estudiantes", "Registrados", String.valueOf(overview.totalStudents())));
        rows.add(List.of("", "Por estado", totals(overview.studentsByStatus())));
        rows.add(List.of("", "Matrículas activas " + overview.academicYear(), String.valueOf(overview.totalEnrolled())));
        rows.add(List.of("Profesores", "Registrados", String.valueOf(overview.totalTeachers())));
        rows.add(List.of("", "Por estado", totals(overview.teachersByStatus())));
        rows.add(List.of("Clubes", "Activos / total", overview.clubs().activeClubs() + " / " + overview.clubs().totalClubs()));
        rows.add(List.of("", "Inscripciones activas", String.valueOf(overview.clubs().activeMemberships())));
        rows.add(List.of("", "Clubes llenos / cupos libres",
                overview.clubs().fullClubs() + " / " + overview.clubs().availableSpots()));
        rows.add(List.of("Eventos", "Próximos / realizados / cancelados",
                overview.events().upcoming() + " / " + overview.events().held() + " / " + overview.events().cancelled()));
        rows.add(List.of("", "Participaciones", String.valueOf(overview.events().participations())));
        rows.add(List.of("Recursos", "Registrados", String.valueOf(overview.totalResources())));
        rows.add(List.of("", "Por estado", totals(overview.resourcesByStatus())));
        rows.add(List.of("Préstamos", "Pendientes / vencidos", overview.openLoans() + " / " + overview.overdueLoans()));
        rows.add(List.of("Finanzas", "Estudiantes en mora", String.valueOf(overview.debtorStudents())));
        rows.add(List.of("", "Total pendiente", Money.format(overview.totalOwed())));
        output.table(List.of("Módulo", "Indicador", "Valor"), rows);

        if (!overview.enrolledByGrade().isEmpty()) {
            output.info("Matrículas activas por grado en " + overview.academicYear());
            output.table(List.of("Grado", "Estudiantes"),
                    overview.enrolledByGrade().entrySet().stream()
                            .map(entry -> List.of(entry.getKey(), String.valueOf(entry.getValue())))
                            .toList());
        }
    }

    private static <E extends Enum<E> & DbEnum> String totals(Map<E, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey().getDisplayName() + " " + entry.getValue())
                .collect(Collectors.joining("  |  "));
    }
}
