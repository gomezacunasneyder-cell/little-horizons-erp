package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.InvoiceLine;
import com.littlehorizons.erp.model.entity.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Turns a payment into an invoice line. The amount a family pays already includes the VAT of its
 * concept, so the line splits it: value without VAT = amount / (1 + VAT%), rounded to cents, and
 * VAT = amount - value without VAT. That way the invoice total is exactly what was paid, to the cent.
 */
public final class InvoiceCalculator {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private InvoiceCalculator() {
    }

    public static InvoiceLine lineOf(Payment payment) {
        int vatPercent = payment.getConcept().getVatPercent();
        BigDecimal amount = payment.getAmount();
        BigDecimal withoutVat = vatPercent == 0 ? amount
                : amount.divide(BigDecimal.valueOf(100 + vatPercent).movePointLeft(2), 2, RoundingMode.HALF_UP);
        return new InvoiceLine(payment.getId(), payment.getReceiptNumber(), describe(payment), 1, withoutVat,
                vatPercent, amount.subtract(withoutVat));
    }

    /** "Pensión mensual - cuota de febrero 2026" or "Uniforme diario 2026". */
    static String describe(Payment payment) {
        String name = payment.getConcept().getName();
        if (payment.getInstallmentMonth() == null) {
            return name + " " + payment.getAcademicYear();
        }
        return name + " - cuota de " + Month.of(payment.getInstallmentMonth()).getDisplayName(TextStyle.FULL, SPANISH)
                + " " + payment.getAcademicYear();
    }
}
