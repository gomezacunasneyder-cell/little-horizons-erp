package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One item the school charges for (annual enrollment, monthly tuition, uniforms, field trips...)
 * with its base value in pesos. The VAT percentage is the one printed on the invoice. A concept is
 * never deleted (payments refer to it): it is deactivated, and then it cannot be charged again.
 */
public class PaymentConcept {

    public static final int NAME_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    /** A very large value is almost always a typing mistake (an extra zero). */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,19}");

    private Integer id;
    private String code;
    private String name;
    private String description;
    private BigDecimal baseAmount;
    private int vatPercent;
    private boolean recurring;
    private boolean active = true;

    public PaymentConcept(String code, String name, String description, BigDecimal baseAmount,
                          int vatPercent, boolean recurring) {
        setCode(code);
        setName(name);
        setDescription(description);
        setBaseAmount(baseAmount);
        setVatPercent(vatPercent);
        this.recurring = recurring;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = checkCode(code);
    }

    /** Upper case letters, digits and underscore, 3 to 20 characters, starting with a letter (ex. PENSION_MENSUAL). */
    public static String checkCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("El código del concepto es obligatorio.");
        }
        String clean = code.trim().toUpperCase();
        if (!CODE.matcher(clean).matches()) {
            throw new ValidationException("El código debe tener de 3 a 20 caracteres: letras, números o guion bajo, "
                    + "empezando por una letra (ejemplo: PENSION_MENSUAL).");
        }
        return clean;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Person.requireLabel(name, NAME_MAX, "El nombre del concepto");
    }

    /** Optional (null when blank). */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = checkDescription(description);
    }

    public static String checkDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String clean = description.trim();
        if (clean.length() > DESCRIPTION_MAX) {
            throw new ValidationException("La descripción admite máximo " + DESCRIPTION_MAX + " caracteres.");
        }
        return clean;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = checkAmount(baseAmount);
    }

    /** More than zero, up to MAX_AMOUNT, with at most two decimals. */
    public static BigDecimal checkAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException("El valor es obligatorio.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("El valor debe ser mayor que cero.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ValidationException("El valor no puede superar " + Money.format(MAX_AMOUNT) + ".");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ValidationException("El valor admite máximo dos decimales.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public int getVatPercent() {
        return vatPercent;
    }

    public void setVatPercent(int vatPercent) {
        if (vatPercent < 0 || vatPercent > 100) {
            throw new ValidationException("El IVA debe estar entre 0 y 100.");
        }
        this.vatPercent = vatPercent;
    }

    /** True for charges that repeat (monthly tuition). */
    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PaymentConcept concept && id != null && Objects.equals(id, concept.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return code + " - " + name;
    }
}
