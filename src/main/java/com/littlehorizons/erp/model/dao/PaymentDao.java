package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.Student;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentDao {

    /**
     * Saves the payment in ONE transaction that also hands out the next receipt number (the payment
     * receives its id and its receipt number). {@code owed} is what the student must pay in total
     * for that concept, year and installment month. Inside the same transaction, and after taking
     * the lock on the receipt counter, the DAO refuses (ValidationException) a payment that would
     * make the total paid exceed {@code owed}. That is the ONLY place where the rule is checked, so
     * two payments made at the same time cannot both pass.
     */
    void create(Payment payment, BigDecimal owed) throws DaoException;

    /** The payments of a student, newest first. */
    List<Payment> findByStudent(Student student) throws DaoException;

    /** What the student already paid for that concept and year (month null = a concept without installments); zero when nothing. */
    BigDecimal paidFor(int studentId, int conceptId, int academicYear, Integer month) throws DaoException;

    /** The total paid per student and month for one concept in one year (only payments with a month). */
    List<PaidInstallment> findInstallments(int conceptId, int academicYear) throws DaoException;
}
