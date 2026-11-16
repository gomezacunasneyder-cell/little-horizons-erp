package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One payment made for a student: the concept paid, the amount, the date, the method and the
 * receipt number. The receipt number is NOT chosen by the user: the system hands it out when the
 * payment is saved (see PaymentDao). A payment of tuition also says which monthly installment it
 * pays (installmentMonth); other concepts have no month. The optional payer is the guardian who paid.
 */
public class Payment {

    private Integer id;
    private final Student student;
    private final PaymentConcept concept;
    private final Guardian payer;
    private final BigDecimal amount;
    private final LocalDate paymentDate;
    private final PaymentMethod method;
    private final int academicYear;
    private final Integer installmentMonth;
    private String receiptNumber;

    public Payment(Student student, PaymentConcept concept, Guardian payer, BigDecimal amount,
                   LocalDate paymentDate, PaymentMethod method, int academicYear, Integer installmentMonth) {
        this.student = Objects.requireNonNull(student, "student");
        this.concept = Objects.requireNonNull(concept, "concept");
        this.payer = payer;
        this.amount = PaymentConcept.checkAmount(amount);
        this.paymentDate = checkDate(paymentDate);
        this.method = Objects.requireNonNull(method, "method");
        this.academicYear = StudentConceptPrice.checkYear(academicYear);
        this.installmentMonth = checkMonth(installmentMonth);
    }

    /** A payment cannot be in the future (the system records money that already arrived). */
    public static LocalDate checkDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("La fecha del pago es obligatoria.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha del pago no puede estar en el futuro.");
        }
        if (date.getYear() < Enrollment.MIN_YEAR) {
            throw new ValidationException("El año debe ser " + Enrollment.MIN_YEAR + " o posterior.");
        }
        return date;
    }

    /** Null (no installment) or a month from 1 to 12. */
    public static Integer checkMonth(Integer month) {
        if (month != null && (month < 1 || month > 12)) {
            throw new ValidationException("El mes de la cuota debe estar entre 1 y 12.");
        }
        return month;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public PaymentConcept getConcept() {
        return concept;
    }

    /** The guardian who paid, or null when it was not recorded. */
    public Guardian getPayer() {
        return payer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    /** The month of the tuition installment this payment covers, or null for other concepts. */
    public Integer getInstallmentMonth() {
        return installmentMonth;
    }

    /** Null until the payment is saved. */
    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }
}
