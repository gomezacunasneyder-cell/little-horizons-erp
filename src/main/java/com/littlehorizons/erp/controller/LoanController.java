package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.loan.LoanAnalyzer;
import com.littlehorizons.erp.controller.loan.LoanListing;
import com.littlehorizons.erp.controller.loan.OverdueReport;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.LoanDao;
import com.littlehorizons.erp.model.dao.ResourceDao;
import com.littlehorizons.erp.model.entity.Borrower;
import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceStatus;
import com.littlehorizons.erp.util.EnumCounts;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Loans and returns. The rules, and where each one lives (a rule is enforced in ONE place):
 * - who may borrow, the dates and the observations: the Loan entity and the Borrower record;
 * - only an available resource can be lent, and a lent one is "Prestado" until it is returned:
 *   guarded UPDATEs inside the transactions of LoanDao (so two people cannot get the same item);
 * - "Vencido" means the scheduled date is before today: the report asks for exactly that
 *   (LoanDao.findOverdue) and LoanDao.markOverdue writes the state "Vencido" in the database, called
 *   here before every read (nothing runs by itself in the database).
 * The date of "today" comes from a Supplier, so the tests can fix it instead of waiting for time to pass.
 */
public class LoanController extends BaseController {

    private static final Map<ResourceStatus, String> NOT_LENDABLE_REASONS = Map.of(
            ResourceStatus.BORROWED, "ya está prestado: debe devolverse antes de prestarlo de nuevo.",
            ResourceStatus.MAINTENANCE, "está en mantenimiento.",
            ResourceStatus.WRITTEN_OFF, "está dado de baja.",
            ResourceStatus.AVAILABLE, "cambió de estado mientras se registraba el préstamo. Intente de nuevo.");

    private final LoanDao loanDao;
    private final ResourceDao resourceDao;
    private final Supplier<LocalDate> today;

    public LoanController(LoanDao loanDao, ResourceDao resourceDao, ErrorLogger logger, Supplier<LocalDate> today) {
        super(logger);
        this.loanDao = loanDao;
        this.resourceDao = resourceDao;
        this.today = today;
    }

    /** The date the loans and returns are registered with (the clock lives here, not in the views). */
    public LocalDate today() {
        return today.get();
    }

    /** The return date proposed when the user presses Enter: {@value Loan#DEFAULT_LOAN_DAYS} days from today. */
    public LocalDate defaultDueDate() {
        return today().plusDays(Loan.DEFAULT_LOAN_DAYS);
    }

    /** Resources that match the text and can be lent now (Disponible); explains why when none can. */
    public Result<List<Resource>> searchLendable(String term) {
        return execute("Search lendable resources", () -> {
            List<Resource> found = searchResources(term);
            List<Resource> lendable = found.stream().filter(r -> r.getStatus() == ResourceStatus.AVAILABLE).toList();
            if (lendable.isEmpty() && !found.isEmpty()) {
                throw new ValidationException("Hay " + found.size() + " recurso(s) con «" + term.trim() + "», pero ninguno "
                        + "está disponible para prestar (" + statesOf(found) + ").");
            }
            return Result.ok(lendable, "");
        });
    }

    /** Resources that match the text and are out on loan now (Prestado); explains why when none is. */
    public Result<List<Resource>> searchBorrowed(String term) {
        return execute("Search borrowed resources", () -> {
            List<Resource> found = searchResources(term);
            List<Resource> borrowed = found.stream().filter(r -> r.getStatus() == ResourceStatus.BORROWED).toList();
            if (borrowed.isEmpty() && !found.isEmpty()) {
                throw new ValidationException("Hay " + found.size() + " recurso(s) con «" + term.trim() + "», pero ninguno "
                        + "está prestado (" + statesOf(found) + ").");
            }
            return Result.ok(borrowed, "");
        });
    }

    /** Registers the loan: today is the loan date. Fails with the reason when the resource cannot be lent. */
    public Result<Loan> lend(Resource resource, Borrower borrower, LocalDate dueDate, String observations) {
        return execute("Register loan", () -> {
            Loan loan = Loan.lend(resource, borrower, today(), dueDate, observations);
            if (!loanDao.lend(loan)) {
                throw new ValidationException(notLendableMessage(resource));
            }
            return Result.ok(loan, "Préstamo registrado: " + resource.getCode() + " «" + resource.getName() + "» para "
                    + borrower.describe() + ". Debe devolverse el " + loan.getDueDate() + ".");
        });
    }

    /** The open loan of a resource that is out; fails when it has none. */
    public Result<Loan> openLoanOf(Resource resource) {
        return execute("Find open loan", () -> {
            Loan loan = loanDao.findOpenByResource(resource.getId()).orElseThrow(() -> new ValidationException(
                    "El recurso " + resource.getCode() + " no tiene un préstamo pendiente."));
            return Result.ok(loan, "");
        });
    }

    /** Registers the return today: the loan gets its real return date and the resource is "Disponible" again. */
    public Result<Loan> giveBack(Loan loan, String note) {
        return execute("Register return", () -> {
            LocalDate returnDate = today();
            loan.registerReturn(returnDate, note);
            if (!loanDao.giveBack(loan)) {
                throw new ValidationException("Este préstamo ya había sido devuelto.");
            }
            long daysLate = loan.daysLate(returnDate);
            String late = daysLate == 0 ? "" : " Se entregó con " + daysLate + " día(s) de retraso.";
            return Result.ok(loan, "Devolución registrada: " + loan.getResource().getCode() + " «"
                    + loan.getResource().getName() + "» devuelto por " + loan.getBorrower().describe() + " el "
                    + returnDate + ". El recurso quedó Disponible." + late);
        });
    }

    /** Every loan that is still out (Activo and Vencido), the earliest due date first. */
    public Result<LoanListing> pending() {
        return execute("List pending loans", () -> {
            loanDao.markOverdue(today());
            LoanListing listing = LoanAnalyzer.listing(loanDao.findOpen());
            return Result.ok(listing, listing.total() == 0 ? "No hay préstamos pendientes." : "");
        });
    }

    /** The loans not returned by their scheduled date, as of today. */
    public Result<OverdueReport> overdueReport() {
        return execute("Overdue loans report", () -> {
            LocalDate asOf = today();
            loanDao.markOverdue(asOf);
            OverdueReport report = LoanAnalyzer.overdueReport(loanDao.findOverdue(asOf), asOf);
            return Result.ok(report, report.total() == 0 ? "No hay préstamos vencidos al " + asOf + "." : "");
        });
    }

    public Result<LoanListing> historyOfResource(Resource resource) {
        return execute("Loan history of a resource", () -> {
            loanDao.markOverdue(today());
            LoanListing listing = LoanAnalyzer.listing(loanDao.findByResource(resource.getId()));
            return Result.ok(listing, listing.total() == 0 ? "El recurso " + resource.getCode() + " nunca se ha prestado." : "");
        });
    }

    public Result<LoanListing> historyOfBorrower(Borrower borrower) {
        return execute("Loan history of a borrower", () -> {
            loanDao.markOverdue(today());
            LoanListing listing = LoanAnalyzer.listing(loanDao.findByBorrower(borrower.type(), borrower.id()));
            return Result.ok(listing, listing.total() == 0 ? borrower.describe() + " no tiene préstamos registrados." : "");
        });
    }

    private List<Resource> searchResources(String term) throws DaoException {
        if (term == null || term.isBlank()) {
            throw new ValidationException("Escriba parte del código, del nombre, de la ubicación o de la categoría.");
        }
        return resourceDao.search(term.trim());
    }

    /** "Prestado 2, En Mantenimiento 1": how many of the resources are in each state (only the states that appear). */
    private static String statesOf(List<Resource> resources) {
        return EnumCounts.countBy(ResourceStatus.class, resources, Resource::getStatus).entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey().getDisplayName() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    /** Why the guarded UPDATE did not change the resource: it is read again, because the one in hand may be old. */
    private String notLendableMessage(Resource resource) throws DaoException {
        String name = resource.getCode() + " «" + resource.getName() + "»";
        ResourceStatus current = resourceDao.findById(resource.getId()).map(Resource::getStatus).orElse(null);
        if (current == null) {
            return "El recurso " + name + " ya no existe.";
        }
        return "No se puede prestar " + name + ": " + NOT_LENDABLE_REASONS.get(current);
    }
}
