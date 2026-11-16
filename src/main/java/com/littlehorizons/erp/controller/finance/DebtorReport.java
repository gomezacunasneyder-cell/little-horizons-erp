package com.littlehorizons.erp.controller.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The students in arrears at a date, the largest debt first, with the total owed. */
public record DebtorReport(LocalDate asOf, List<DebtorEntry> debtors) {

    public BigDecimal totalPending() {
        return debtors.stream().map(DebtorEntry::totalPending).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int overdueInstallments() {
        return debtors.stream().mapToInt(d -> d.installments().size()).sum();
    }
}
