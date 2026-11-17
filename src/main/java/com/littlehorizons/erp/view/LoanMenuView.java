package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.LoanController;
import com.littlehorizons.erp.controller.ResourceController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.TeacherController;
import com.littlehorizons.erp.controller.loan.LoanListing;
import com.littlehorizons.erp.controller.loan.OverdueReport;
import com.littlehorizons.erp.model.entity.Borrower;
import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.entity.Resource;

import java.time.LocalDate;
import java.util.List;

/**
 * "Gestión de Recursos - Préstamos y devoluciones": register a loan to a student or a teacher,
 * register the return, see what is still out and the overdue loans. It only asks and prints;
 * who may borrow, the dates, the states of the resource and when a loan is overdue are decided
 * by the Loan entity, the controller and the DAO.
 */
public final class LoanMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Registrar un préstamo",
            "Registrar una devolución",
            "Ver los préstamos pendientes (activos y vencidos)",
            "Reporte de préstamos vencidos",
            "Historial de préstamos de un recurso",
            "Historial de préstamos de un estudiante o profesor");

    private final LoanController loans;
    private final ResourceSelector lendableSelector;
    private final ResourceSelector borrowedSelector;
    private final ResourceSelector anyResourceSelector;
    private final BorrowerSelector borrowerSelector;
    private final LoanForm form;
    private final LoanPrinter loanPrinter;
    private final ResultPrinter printer;

    public LoanMenuView(ConsoleInput input, ConsoleOutput output, LoanController loans, ResourceController resources,
                        StudentController students, TeacherController teachers) {
        super(input, output);
        this.loans = loans;
        this.lendableSelector = new ResourceSelector(input, output, loans::searchLendable);
        this.borrowedSelector = new ResourceSelector(input, output, loans::searchBorrowed);
        this.anyResourceSelector = new ResourceSelector(input, output, resources::search);
        this.borrowerSelector = new BorrowerSelector(input, output, students, teachers);
        this.form = new LoanForm(input);
        this.loanPrinter = new LoanPrinter(output);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "GESTIÓN DE RECURSOS - PRÉSTAMOS Y DEVOLUCIONES";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> lend();
            case 2 -> giveBack();
            case 3 -> showListing(loans.pending());
            case 4 -> showOverdue();
            case 5 -> showListing(loans.historyOfResource(anyResourceSelector.select()));
            case 6 -> showListing(loans.historyOfBorrower(borrowerSelector.select()));
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void lend() {
        output.info("Solo se muestran los recursos Disponibles.");
        Resource resource = lendableSelector.select();
        Borrower borrower = borrowerSelector.select();
        LocalDate dueDate = form.readDueDate(loans.defaultDueDate());
        String observations = form.readObservations("Observaciones (opcional, Enter para omitir): ");
        printer.report(loans.lend(resource, borrower, dueDate, observations));
    }

    private void giveBack() {
        output.info("Solo se muestran los recursos Prestados.");
        Resource resource = borrowedSelector.select();
        Result<Loan> open = loans.openLoanOf(resource);
        if (!open.isSuccess()) {
            output.error(open.getMessage());
            return;
        }
        output.info("Préstamo pendiente:");
        loanPrinter.detail(open.getData(), loans.today());
        String note = form.readObservations("Observaciones de la devolución (opcional, Enter para omitir): ");
        if (input.readYesNo("¿Registrar la devolución con la fecha de hoy?")) {
            printer.report(loans.giveBack(open.getData(), note));
        } else {
            output.info("No se registró la devolución.");
        }
    }

    private void showListing(Result<LoanListing> result) {
        if (result.isSuccess()) {
            if (result.getData().total() == 0) {
                output.info(result.getMessage());
            } else {
                loanPrinter.listing(result.getData());
            }
        } else {
            output.error(result.getMessage());
        }
    }

    void showOverdue() {
        Result<OverdueReport> result = loans.overdueReport();
        if (!result.isSuccess()) {
            output.error(result.getMessage());
        } else if (result.getData().total() == 0) {
            output.info(result.getMessage());
        } else {
            loanPrinter.overdue(result.getData());
        }
    }
}
