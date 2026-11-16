package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.finance.PaymentAnalyzer;
import com.littlehorizons.erp.controller.finance.PaymentBalance;
import com.littlehorizons.erp.controller.finance.PaymentHistory;
import com.littlehorizons.erp.controller.finance.PaymentSchedule;
import com.littlehorizons.erp.controller.finance.StudentPayments;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.EnrollmentDao;
import com.littlehorizons.erp.model.dao.PaymentConceptDao;
import com.littlehorizons.erp.model.dao.PaymentDao;
import com.littlehorizons.erp.model.dao.StudentConceptPriceDao;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;
import com.littlehorizons.erp.model.enums.EnrollmentStatus;
import com.littlehorizons.erp.model.enums.PaymentMethod;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Registers payments and answers their history. A payment is always for what the student really
 * owes: the base value of the concept or his personalized value (discount, scholarship). Tuition
 * is paid by monthly installments, and any concept can be paid in parts, but never above what is
 * owed (that last rule is guarded by the DAO inside the transaction that gives the receipt number).
 */
public class PaymentController extends BaseController {

    private final PaymentDao paymentDao;
    private final PaymentConceptDao conceptDao;
    private final StudentConceptPriceDao priceDao;
    private final EnrollmentDao enrollmentDao;
    private final StudentDao studentDao;

    public PaymentController(PaymentDao paymentDao, PaymentConceptDao conceptDao, StudentConceptPriceDao priceDao,
                             EnrollmentDao enrollmentDao, StudentDao studentDao, ErrorLogger logger) {
        super(logger);
        this.paymentDao = paymentDao;
        this.conceptDao = conceptDao;
        this.priceDao = priceDao;
        this.enrollmentDao = enrollmentDao;
        this.studentDao = studentDao;
    }

    /** What the student owes for the concept (and installment month), what he paid and what is left. */
    public Result<PaymentBalance> balance(Student student, PaymentConcept concept, int academicYear, Integer month) {
        return execute("Calculate payment balance", () -> Result.ok(balanceOf(student, concept, academicYear,
                PaymentSchedule.checkMonth(concept, month)), ""));
    }

    /**
     * Registers a payment and gives it the next receipt number. {@code amount} null means "pay everything that is pending".
     * {@code payer} is the guardian who paid (null when it is not recorded) and must be a guardian of the student.
     */
    public Result<Payment> register(Student student, PaymentConcept requestedConcept, Guardian payer, BigDecimal amount,
                                    LocalDate date, PaymentMethod method, int academicYear, Integer month) {
        return execute("Register payment", () -> {
            // read the concept again: it may have been deactivated or its value changed after the user chose it
            PaymentConcept concept = conceptDao.findById(requestedConcept.getId())
                    .orElseThrow(() -> new ValidationException("El concepto ya no existe."));
            if (!concept.isActive()) {
                throw new ValidationException("El concepto «" + concept.getName() + "» está inactivo y ya no se puede cobrar.");
            }
            Integer installment = PaymentSchedule.checkMonth(concept, month);
            if (concept.isRecurring()) {
                requireActiveEnrollment(student, academicYear);
            }
            requireGuardianOf(student, payer);
            PaymentBalance balance = balanceOf(student, concept, academicYear, installment);
            BigDecimal toPay = amount;
            if (toPay == null) {
                if (balance.pending().signum() == 0) {
                    throw new ValidationException("No hay nada pendiente: " + describe(concept, installment)
                            + " está pagado por completo (valor " + Money.format(balance.owed()) + ").");
                }
                toPay = balance.pending();
            }
            Payment payment = new Payment(student, concept, payer, toPay, date, method, academicYear, installment);
            paymentDao.create(payment, balance.owed());
            BigDecimal stillPending = balance.pending().subtract(toPay);
            return Result.ok(payment, "Pago registrado. Recibo " + payment.getReceiptNumber() + " por " + Money.format(toPay)
                    + " de " + student.getFullName() + " (" + describe(concept, installment) + "). "
                    + (stillPending.signum() == 0 ? "Quedó pagado por completo."
                    : "Quedan pendientes " + Money.format(stillPending) + "."));
        });
    }

    public Result<PaymentHistory> historyOfStudent(Student student) {
        return execute("Show student payment history", () -> Result.ok(
                PaymentAnalyzer.history(List.of(new StudentPayments(student, paymentDao.findByStudent(student)))), ""));
    }

    /** All the payments of every child of the guardian, grouped by child. */
    public Result<PaymentHistory> historyOfGuardian(Guardian guardian) {
        return execute("Show guardian payment history", () -> {
            List<Student> children = studentDao.findByGuardian(guardian.getId());
            if (children.isEmpty()) {
                throw new ValidationException(guardian.getFullName() + " no tiene estudiantes asociados.");
            }
            List<StudentPayments> parts = new java.util.ArrayList<>();
            for (Student child : children) {
                parts.add(new StudentPayments(child, paymentDao.findByStudent(child)));
            }
            return Result.ok(PaymentAnalyzer.history(parts), "");
        });
    }

    private PaymentBalance balanceOf(Student student, PaymentConcept concept, int year, Integer month) throws DaoException {
        BigDecimal owed = priceDao.find(student, concept, StudentConceptPrice.checkYear(year))
                .map(StudentConceptPrice::getAmount).orElse(concept.getBaseAmount());
        return new PaymentBalance(owed, paymentDao.paidFor(student.getId(), concept.getId(), year, month));
    }

    private void requireActiveEnrollment(Student student, int year) throws DaoException {
        boolean active = enrollmentDao.findByStudentAndYear(student.getId(), year)
                .map(e -> e.getStatus() == EnrollmentStatus.ACTIVE).orElse(false);
        if (!active) {
            throw new ValidationException(student.getFullName() + " no tiene matrícula activa en " + year
                    + ", así que no se le puede registrar una cuota de ese año.");
        }
    }

    private void requireGuardianOf(Student student, Guardian payer) throws DaoException {
        if (payer != null && studentDao.findGuardians(student.getId()).stream()
                .noneMatch(link -> link.getGuardian().getId().equals(payer.getId()))) {
            throw new ValidationException(payer.getFullName() + " no es acudiente de " + student.getFullName() + ".");
        }
    }

    private static String describe(PaymentConcept concept, Integer month) {
        return month == null ? concept.getName() : concept.getName() + " cuota " + month;
    }
}
