package com.littlehorizons.erp.controller.finance;

import java.math.BigDecimal;

/** What a student owes for a concept (and installment), what he already paid and what is left. */
public record PaymentBalance(BigDecimal owed, BigDecimal paid) {

    public BigDecimal pending() {
        return owed.subtract(paid).max(BigDecimal.ZERO);
    }
}
