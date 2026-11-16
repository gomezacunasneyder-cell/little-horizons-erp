package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.PaymentConcept;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One monthly installment that is still unpaid (or partly paid) more than one month after its due date. */
public record OverdueInstallment(PaymentConcept concept, int month, LocalDate dueDate, BigDecimal pending) {
}
