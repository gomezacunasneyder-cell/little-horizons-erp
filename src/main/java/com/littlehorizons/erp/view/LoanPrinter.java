package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.loan.LoanListing;
import com.littlehorizons.erp.controller.loan.OverdueReport;
import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.enums.DbEnum;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Prints loans as tables and lines. It only formats; the totals and the days late come already calculated. */
final class LoanPrinter {

    private final ConsoleOutput output;

    LoanPrinter(ConsoleOutput output) {
        this.output = output;
    }

    /** Loans of any state, with the totals per state under the table. */
    void listing(LoanListing listing) {
        output.table(List.of("Recurso", "Solicitante", "Tipo", "Préstamo", "Vence", "Devuelto", "Estado"),
                listing.loans().stream().map(this::row).toList());
        if (listing.total() > 0) {
            output.info("Total: " + listing.total() + " préstamo(s).");
            output.info("Por estado: " + totals(listing.byStatus()));
        }
    }

    private List<String> row(Loan loan) {
        return List.of(loan.getResource().getCode() + " " + loan.getResource().getName(), loan.getBorrower().fullName(),
                loan.getBorrowerType().getDisplayName(), loan.getLoanDate().toString(), loan.getDueDate().toString(),
                loan.getReturnDate() == null ? "-" : loan.getReturnDate().toString(), loan.getStatus().getDisplayName());
    }

    void overdue(OverdueReport report) {
        output.table(List.of("Código", "Recurso", "Solicitante", "Tipo", "Contacto", "Vencía", "Días de retraso"),
                report.loans().stream().map(loan -> overdueRow(loan, report.asOf())).toList());
        if (report.total() > 0) {
            output.info("Préstamos vencidos al " + report.asOf() + ": " + report.total() + ". Mayor retraso: "
                    + report.maxDaysLate() + " día(s).");
            output.info("Por solicitante: " + totals(report.byBorrowerType()));
            output.info("Por categoría:   " + totals(report.byCategory()));
        }
    }

    private List<String> overdueRow(Loan loan, LocalDate asOf) {
        String contact = loan.getBorrower().contact();
        return List.of(loan.getResource().getCode(), loan.getResource().getName(), loan.getBorrower().fullName(),
                loan.getBorrowerType().getDisplayName(), contact == null ? "-" : contact, loan.getDueDate().toString(),
                String.valueOf(loan.daysLate(asOf)));
    }

    /** The open loan that is about to be returned, shown before asking for confirmation. */
    void detail(Loan loan, LocalDate today) {
        output.line("  Recurso:           " + loan.getResource().getCode() + " - " + loan.getResource().getName());
        output.line("  Solicitante:       " + loan.getBorrower().describe());
        output.line("  Fecha de préstamo: " + loan.getLoanDate());
        output.line("  Devolución prog.:  " + loan.getDueDate()
                + (loan.daysLate(today) > 0 ? "  (" + loan.daysLate(today) + " día(s) de retraso)" : ""));
        output.line("  Estado:            " + loan.getStatus().getDisplayName());
        output.line("  Observaciones:     " + (loan.getObservations() == null ? "-" : loan.getObservations()));
    }

    private static <E extends Enum<E> & DbEnum> String totals(Map<E, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey().getDisplayName() + " " + entry.getValue())
                .collect(Collectors.joining("  |  "));
    }
}
