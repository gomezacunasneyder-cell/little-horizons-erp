package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StatisticsController;
import com.littlehorizons.erp.controller.report.SchoolOverview;

import java.util.List;

/**
 * "Reportes y Estadísticas": one place to reach every report of the system. The general overview
 * is new; the other options open the SAME screens the modules already have (the report is
 * written once, in its module, and this menu only points to it), so what the user sees here is
 * exactly what the module shows.
 */
public final class ReportsMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Resumen general del colegio",
            "Promedios de un curso por materia",
            "Patrones de inasistencia",
            "Estudiantes en mora",
            "Ocupación de clubes",
            "Calendario de eventos",
            "Inventario de recursos (con filtros)",
            "Préstamos vencidos");

    private final StatisticsController statistics;
    private final StatisticsPrinter printer;
    private final GradeMenuView grades;
    private final AttendanceMenuView attendance;
    private final PaymentMenuView payments;
    private final ClubMenuView clubs;
    private final EventMenuView events;
    private final ResourceMenuView resources;
    private final LoanMenuView loans;

    public ReportsMenuView(ConsoleInput input, ConsoleOutput output, StatisticsController statistics,
                           GradeMenuView grades, AttendanceMenuView attendance, PaymentMenuView payments,
                           ClubMenuView clubs, EventMenuView events, ResourceMenuView resources, LoanMenuView loans) {
        super(input, output);
        this.statistics = statistics;
        this.printer = new StatisticsPrinter(output);
        this.grades = grades;
        this.attendance = attendance;
        this.payments = payments;
        this.clubs = clubs;
        this.events = events;
        this.resources = resources;
        this.loans = loans;
    }

    @Override
    protected String title() {
        return "REPORTES Y ESTADÍSTICAS";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> showOverview();
            case 2 -> grades.showCourseAverages();
            case 3 -> attendance.showAbsenceReport();
            case 4 -> payments.debtorsReport();
            case 5 -> clubs.showCatalog();
            case 6 -> events.showCalendar();
            case 7 -> resources.filter();
            case 8 -> loans.showOverdue();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void showOverview() {
        Result<SchoolOverview> result = statistics.overview();
        if (result.isSuccess()) {
            printer.overview(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }
}
