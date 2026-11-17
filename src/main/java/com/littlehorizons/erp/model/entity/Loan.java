package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.BorrowerType;
import com.littlehorizons.erp.model.enums.LoanStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One loan of a resource to a student or a teacher: who, which resource, the loan date, the
 * scheduled return date, the real return date (empty while it is out) and the state.
 *
 * A NEW loan is created with {@link #lend}, which validates (active borrower, return date not
 * before the loan date and not more than {@value #MAX_LOAN_DAYS} days later); a loan read from the
 * database is rebuilt with {@link #restore}, which trusts the stored data. The only change a loan
 * can have afterwards is {@link #registerReturn}.
 *
 * The loan tables are two (students and teachers) and each has its own AUTO_INCREMENT, so the
 * number alone does not identify a loan: the identity is the pair (borrower type, id).
 */
public class Loan {

    /** Longest loan period we allow when registering (a policy of this program, not of the database). */
    public static final int MAX_LOAN_DAYS = 90;
    /** Return period proposed when the user just presses Enter. */
    public static final int DEFAULT_LOAN_DAYS = 7;
    public static final int OBSERVATIONS_MAX = 500;

    private Integer id;
    private final Resource resource;
    private final Borrower borrower;
    private final LocalDate loanDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;
    private LoanStatus status;
    private String observations;

    private Loan(Integer id, Resource resource, Borrower borrower, LocalDate loanDate, LocalDate dueDate,
                 LocalDate returnDate, LoanStatus status, String observations) {
        this.id = id;
        this.resource = resource;
        this.borrower = borrower;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.status = status;
        this.observations = observations;
    }

    /** A new loan: state "Activo", not returned yet. Throws ValidationException when something is wrong. */
    public static Loan lend(Resource resource, Borrower borrower, LocalDate loanDate, LocalDate dueDate,
                            String observations) {
        if (resource == null || resource.getId() == null) {
            throw new ValidationException("Seleccione el recurso que se va a prestar.");
        }
        if (borrower == null) {
            throw new ValidationException("Seleccione quién solicita el recurso.");
        }
        if (!borrower.active()) {
            throw new ValidationException(borrower.describe() + " está «" + borrower.statusLabel() + "»: solo "
                    + "estudiantes y profesores activos pueden solicitar recursos.");
        }
        return new Loan(null, resource, borrower, loanDate, checkDueDate(loanDate, dueDate), null,
                LoanStatus.ACTIVE, checkObservations(observations));
    }

    /** Rebuilds a loan stored in the database. */
    public static Loan restore(int id, Resource resource, Borrower borrower, LocalDate loanDate, LocalDate dueDate,
                               LocalDate returnDate, LoanStatus status, String observations) {
        return new Loan(id, resource, borrower, loanDate, dueDate, returnDate, status, observations);
    }

    /** Public so the form can say what the allowed range is. */
    public static LocalDate checkDueDate(LocalDate loanDate, LocalDate dueDate) {
        if (dueDate == null) {
            throw new ValidationException("La fecha de devolución programada es obligatoria.");
        }
        if (dueDate.isBefore(loanDate)) {
            throw new ValidationException("La fecha de devolución programada no puede ser anterior a la del préstamo ("
                    + loanDate + ").");
        }
        if (dueDate.isAfter(loanDate.plusDays(MAX_LOAN_DAYS))) {
            throw new ValidationException("Un préstamo no puede durar más de " + MAX_LOAN_DAYS + " días: la fecha "
                    + "programada debe ser a más tardar el " + loanDate.plusDays(MAX_LOAN_DAYS) + ".");
        }
        return dueDate;
    }

    public static String checkObservations(String observations) {
        if (observations == null || observations.isBlank()) {
            return null;
        }
        String clean = observations.trim();
        if (clean.length() > OBSERVATIONS_MAX) {
            throw new ValidationException("Las observaciones admiten máximo " + OBSERVATIONS_MAX + " caracteres.");
        }
        return clean;
    }

    /**
     * Marks the loan as returned on that date. A loan that is already returned cannot be returned
     * again, and a resource cannot come back before it left. A note given now is added to the
     * observations written when the loan was registered.
     */
    public void registerReturn(LocalDate date, String note) {
        if (!status.isOpen()) {
            throw new ValidationException("Este préstamo ya fue devuelto el " + returnDate + ".");
        }
        if (date.isBefore(loanDate)) {
            throw new ValidationException("La fecha de devolución (" + date + ") no puede ser anterior a la del "
                    + "préstamo (" + loanDate + ").");
        }
        String cleanNote = checkObservations(note);
        String joined = observations == null ? cleanNote : cleanNote == null ? observations : observations + " | " + cleanNote;
        this.observations = checkObservations(joined);
        this.returnDate = date;
        this.status = LoanStatus.RETURNED;
    }

    /**
     * Days after the scheduled date: until the real return date when it was returned, otherwise
     * until {@code asOf}. Never negative (a loan that is on time has 0 days late).
     */
    public long daysLate(LocalDate asOf) {
        LocalDate until = returnDate != null ? returnDate : asOf;
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, until));
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Resource getResource() {
        return resource;
    }

    public Borrower getBorrower() {
        return borrower;
    }

    public BorrowerType getBorrowerType() {
        return borrower.type();
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    /** Null while the resource has not been returned. */
    public LocalDate getReturnDate() {
        return returnDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    /** Optional (null when there are none). */
    public String getObservations() {
        return observations;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Loan loan && id != null && Objects.equals(id, loan.id)
                && getBorrowerType() == loan.getBorrowerType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getBorrowerType());
    }

    @Override
    public String toString() {
        return resource.getCode() + " -> " + borrower.fullName() + " (" + status.getDisplayName() + ")";
    }
}
