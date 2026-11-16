package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Student;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A student in arrears: his overdue installments and the guardian to contact (null if none is recorded). */
public record DebtorEntry(Student student, Guardian contact, List<OverdueInstallment> installments) {

    public BigDecimal totalPending() {
        return installments.stream().map(OverdueInstallment::pending).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public LocalDate oldestDueDate() {
        return installments.stream().map(OverdueInstallment::dueDate).min(LocalDate::compareTo).orElseThrow();
    }
}
