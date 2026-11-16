package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** The history asked for a student (one entry) or a guardian (one entry per child), with the totals. */
public record PaymentHistory(List<StudentPayments> students, BigDecimal total, int paymentCount,
                             Map<PaymentMethod, BigDecimal> totalByMethod) {
}
