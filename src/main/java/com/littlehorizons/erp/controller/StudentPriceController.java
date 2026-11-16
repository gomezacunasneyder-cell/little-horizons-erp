package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.finance.ConceptAnalyzer;
import com.littlehorizons.erp.controller.finance.PriceLine;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.PaymentConceptDao;
import com.littlehorizons.erp.model.dao.StudentConceptPriceDao;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.Money;

import java.util.List;
import java.util.Optional;

/**
 * Personalized values (sibling discounts, partial scholarships): only ACTIVE students, only
 * ACTIVE concepts, never higher than the base value. It also answers "how much does this student
 * pay for this concept?", which the payments use to charge the right amount.
 */
public class StudentPriceController extends BaseController {

    private final StudentConceptPriceDao priceDao;
    private final PaymentConceptDao conceptDao;

    public StudentPriceController(StudentConceptPriceDao priceDao, PaymentConceptDao conceptDao, ErrorLogger logger) {
        super(logger);
        this.priceDao = priceDao;
        this.conceptDao = conceptDao;
    }

    /** Creates the personalized value, or replaces the one the student already had for that concept and year. */
    public Result<StudentConceptPrice> assign(StudentConceptPrice price) {
        return execute("Assign personalized price", () -> {
            Student student = price.getStudent();
            if (student.getStatus() != StudentStatus.ACTIVE) {
                throw new ValidationException("Solo los estudiantes activos pueden tener un valor personalizado ("
                        + student.getFullName() + " está " + student.getStatus().getDisplayName().toLowerCase() + ").");
            }
            // read the concept again: its value or state may have changed since the user picked it
            PaymentConcept current = conceptDao.findById(price.getConcept().getId())
                    .orElseThrow(() -> new ValidationException("El concepto ya no existe."));
            if (!current.isActive()) {
                throw new ValidationException("El concepto «" + current.getName() + "» está inactivo.");
            }
            StudentConceptPrice checked = new StudentConceptPrice(student, current, price.getAcademicYear(),
                    price.getAmount(), price.getReason()); // validates the amount against the CURRENT base value
            boolean replaced = priceDao.find(student, current, checked.getAcademicYear()).isPresent();
            priceDao.save(checked);
            PriceLine line = ConceptAnalyzer.lineFor(current, Optional.of(checked));
            return Result.ok(checked, "Valor " + (replaced ? "actualizado" : "asignado") + ": " + student.getFullName()
                    + " pagará " + Money.format(checked.getAmount()) + " por «" + current.getName() + "» en "
                    + checked.getAcademicYear() + " (" + line.discountPercent().stripTrailingZeros().toPlainString()
                    + "% menos que el valor base).");
        });
    }

    public Result<Void> remove(Student student, PaymentConcept concept, int academicYear) {
        return execute("Remove personalized price", () -> {
            if (!priceDao.delete(student, concept, academicYear)) {
                throw new ValidationException(student.getFullName() + " no tiene un valor personalizado para «"
                        + concept.getName() + "» en " + academicYear + ".");
            }
            return Result.ok("Valor personalizado quitado: " + student.getFullName() + " vuelve a pagar "
                    + Money.format(concept.getBaseAmount()) + " por «" + concept.getName() + "» en " + academicYear + ".");
        });
    }

    /** What the student pays for each active concept in the year. */
    public Result<List<PriceLine>> priceList(Student student, int academicYear) {
        return execute("Show student prices", () -> Result.ok(ConceptAnalyzer.priceList(
                conceptDao.findAll(), priceDao.findByStudent(student, academicYear)), ""));
    }

    /** What the student pays for one concept in the year (the base value when there is no personalized one). */
    public Result<PriceLine> priceOf(Student student, PaymentConcept concept, int academicYear) {
        return execute("Find student price", () -> Result.ok(ConceptAnalyzer.lineFor(concept,
                priceDao.find(student, concept, academicYear)), ""));
    }
}
