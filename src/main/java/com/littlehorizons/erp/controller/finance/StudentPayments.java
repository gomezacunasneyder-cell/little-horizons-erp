package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.Payment;
import com.littlehorizons.erp.model.entity.Student;

import java.math.BigDecimal;
import java.util.List;

/** The payments of one student, newest first. */
public record StudentPayments(Student student, List<Payment> payments) {

    public BigDecimal total() {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
