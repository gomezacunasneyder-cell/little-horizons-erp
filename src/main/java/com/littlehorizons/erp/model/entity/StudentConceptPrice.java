package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The value that ONE student pays for ONE concept in ONE academic year, when it is different from
 * the base value (a discount for siblings, a partial scholarship). It can be lower than the base
 * value or equal to it, never higher, and zero means a full scholarship. The reason is required so
 * that anybody reading the record knows why the student pays less.
 */
public class StudentConceptPrice {

    public static final int REASON_MAX = 150;

    private final Student student;
    private final PaymentConcept concept;
    private final int academicYear;
    private final BigDecimal amount;
    private final String reason;

    public StudentConceptPrice(Student student, PaymentConcept concept, int academicYear,
                               BigDecimal amount, String reason) {
        this.student = Objects.requireNonNull(student, "student");
        this.concept = Objects.requireNonNull(concept, "concept");
        this.academicYear = checkYear(academicYear);
        this.amount = checkAmount(amount, concept);
        this.reason = checkReason(reason);
    }

    public static int checkYear(int year) {
        int maxYear = LocalDate.now().getYear() + 1;
        if (year < Enrollment.MIN_YEAR || year > maxYear) {
            throw new ValidationException("El año académico debe estar entre " + Enrollment.MIN_YEAR + " y " + maxYear + ".");
        }
        return year;
    }

    /** From zero up to the base value of the concept, with at most two decimals. */
    public static BigDecimal checkAmount(BigDecimal amount, PaymentConcept concept) {
        if (amount == null) {
            throw new ValidationException("El valor es obligatorio.");
        }
        if (amount.signum() < 0) {
            throw new ValidationException("El valor no puede ser negativo.");
        }
        if (amount.compareTo(concept.getBaseAmount()) > 0) {
            throw new ValidationException("El valor personalizado no puede superar el valor base de «"
                    + concept.getName() + "» (" + Money.format(concept.getBaseAmount()) + ").");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ValidationException("El valor admite máximo dos decimales.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String checkReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("El motivo es obligatorio (por ejemplo: descuento por hermanos).");
        }
        String clean = reason.trim();
        if (clean.length() > REASON_MAX) {
            throw new ValidationException("El motivo admite máximo " + REASON_MAX + " caracteres.");
        }
        return clean;
    }

    public Student getStudent() {
        return student;
    }

    public PaymentConcept getConcept() {
        return concept;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }
}
