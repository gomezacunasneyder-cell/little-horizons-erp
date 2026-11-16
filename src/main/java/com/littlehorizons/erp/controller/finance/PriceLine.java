package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.PaymentConcept;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a student pays for a concept in a year: the base value, the value that really applies
 * (personalized or not) and, when personalized, the reason.
 */
public record PriceLine(PaymentConcept concept, BigDecimal effectiveAmount, String reason) {

    public BigDecimal baseAmount() {
        return concept.getBaseAmount();
    }

    public boolean isPersonalized() {
        return reason != null;
    }

    /** Percentage the student saves, with one decimal (a full scholarship is 100.0). */
    public BigDecimal discountPercent() {
        return baseAmount().subtract(effectiveAmount)
                .multiply(BigDecimal.valueOf(100))
                .divide(baseAmount(), 1, RoundingMode.HALF_UP);
    }
}
