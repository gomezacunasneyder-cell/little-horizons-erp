package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * One line of an invoice: the paid payment it comes from, what was sold (description, quantity,
 * unit value WITHOUT VAT) and the VAT. The numbers must add up: subtotal = quantity x unit value
 * and total = subtotal + VAT, so a line can never be created with inconsistent amounts.
 */
public final class InvoiceLine {

    private final int paymentId;
    private final String receiptNumber;
    private final String description;
    private final int quantity;
    private final BigDecimal unitValue;
    private final int vatPercent;
    private final BigDecimal subtotal;
    private final BigDecimal vatAmount;
    private final BigDecimal total;

    public InvoiceLine(int paymentId, String receiptNumber, String description, int quantity, BigDecimal unitValue,
                       int vatPercent, BigDecimal vatAmount) {
        if (description == null || description.isBlank() || description.length() > 200) {
            throw new ValidationException("La descripción de la línea es obligatoria (máximo 200 caracteres).");
        }
        if (quantity < 1) {
            throw new ValidationException("La cantidad debe ser al menos 1.");
        }
        if (vatPercent < 0 || vatPercent > 100) {
            throw new ValidationException("El porcentaje de IVA debe estar entre 0 y 100.");
        }
        Objects.requireNonNull(unitValue, "unitValue");
        Objects.requireNonNull(vatAmount, "vatAmount");
        if (unitValue.signum() <= 0 || vatAmount.signum() < 0) {
            throw new ValidationException("El valor unitario debe ser mayor que cero y el IVA no puede ser negativo.");
        }
        this.paymentId = paymentId;
        this.receiptNumber = receiptNumber;
        this.description = description.trim();
        this.quantity = quantity;
        this.unitValue = unitValue.setScale(2, RoundingMode.UNNECESSARY);
        this.vatPercent = vatPercent;
        this.subtotal = this.unitValue.multiply(BigDecimal.valueOf(quantity));
        this.vatAmount = vatAmount.setScale(2, RoundingMode.UNNECESSARY);
        this.total = subtotal.add(this.vatAmount);
    }

    public int getPaymentId() {
        return paymentId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    /** Value of one unit, without VAT. */
    public BigDecimal getUnitValue() {
        return unitValue;
    }

    public int getVatPercent() {
        return vatPercent;
    }

    /** Quantity x unit value, without VAT. */
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    /** Subtotal plus VAT: what the payer really paid for this line. */
    public BigDecimal getTotal() {
        return total;
    }
}
