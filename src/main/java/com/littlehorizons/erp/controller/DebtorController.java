package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.finance.ConceptInstallments;
import com.littlehorizons.erp.controller.finance.DebtorEntry;
import com.littlehorizons.erp.controller.finance.DebtorReport;
import com.littlehorizons.erp.controller.finance.PaymentAnalyzer;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.dao.EnrollmentDao;
import com.littlehorizons.erp.model.dao.PaymentConceptDao;
import com.littlehorizons.erp.model.dao.PaymentDao;
import com.littlehorizons.erp.model.dao.StudentConceptPriceDao;
import com.littlehorizons.erp.model.dao.StudentDao;
import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentGuardian;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Report of students in arrears: those with an installment that has been pending for MORE than
 * one month. It reads the data (active enrollments, recurring concepts, personalized values,
 * payments) and lets PaymentAnalyzer do the calculation.
 */
public class DebtorController extends BaseController {

    private final EnrollmentDao enrollmentDao;
    private final PaymentConceptDao conceptDao;
    private final StudentConceptPriceDao priceDao;
    private final PaymentDao paymentDao;
    private final StudentDao studentDao;

    public DebtorController(EnrollmentDao enrollmentDao, PaymentConceptDao conceptDao, StudentConceptPriceDao priceDao,
                            PaymentDao paymentDao, StudentDao studentDao, ErrorLogger logger) {
        super(logger);
        this.enrollmentDao = enrollmentDao;
        this.conceptDao = conceptDao;
        this.priceDao = priceDao;
        this.paymentDao = paymentDao;
        this.studentDao = studentDao;
    }

    /** The debtors of the academic year of {@code today}, calculated as of that date. */
    public Result<DebtorReport> report(LocalDate today) {
        return execute("Generate debtors report", () -> {
            int year = today.getYear();
            List<ConceptInstallments> concepts = new ArrayList<>();
            for (PaymentConcept concept : conceptDao.findAll()) {
                if (concept.isActive() && concept.isRecurring()) {
                    concepts.add(new ConceptInstallments(concept, priceDao.amountsByStudent(concept.getId(), year),
                            paymentDao.findInstallments(concept.getId(), year)));
                }
            }
            List<DebtorEntry> debtors = new ArrayList<>();
            for (DebtorEntry entry : PaymentAnalyzer.debtors(enrollmentDao.findActiveByYear(year), concepts, today)) {
                debtors.add(new DebtorEntry(entry.student(), contactOf(entry.student()), entry.installments()));
            }
            String message = debtors.isEmpty() ? "No hay estudiantes con pagos pendientes por más de un mes al " + today + "." : "";
            return Result.ok(new DebtorReport(today, debtors), message);
        });
    }

    /** The guardian to call: the financially responsible one (the main guardian first), or the main guardian. */
    private Guardian contactOf(Student student) throws DaoException {
        List<StudentGuardian> links = studentDao.findGuardians(student.getId());
        return links.stream()
                .filter(l -> l.getGuardian().isFinanciallyResponsible())
                .max(Comparator.comparing(StudentGuardian::isPrincipal))
                .or(() -> links.stream().filter(StudentGuardian::isPrincipal).findFirst())
                .map(StudentGuardian::getGuardian)
                .orElse(null);
    }
}
