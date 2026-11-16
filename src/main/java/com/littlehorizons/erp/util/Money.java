package com.littlehorizons.erp.util;

import com.littlehorizons.erp.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Colombian pesos: reads what the user types and prints amounts with dots for thousands
 * ($1.500.000). Amounts are BigDecimal with two decimals, never double, so the cents are exact.
 */
public final class Money {

    private static final Pattern AMOUNT = Pattern.compile("\\d{1,10}(\\.\\d{1,2})?");
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO"));

    private Money() {
    }

    /** "1500000" or "1500000.50" (no thousand separators, a dot for the cents). Returns the amount with scale 2. */
    public static BigDecimal parse(String text) {
        String clean = text == null ? "" : text.trim();
        if (!AMOUNT.matcher(clean).matches()) {
            throw new ValidationException("Escriba el valor en pesos solo con números, sin puntos de miles ni comas "
                    + "(ejemplo: 650000). Para centavos use un punto (ejemplo: 650000.50).");
        }
        return new BigDecimal(clean).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** $1.500.000 for whole pesos and $1.500.000,50 when there are cents. */
    public static String format(BigDecimal amount) {
        boolean whole = amount.stripTrailingZeros().scale() <= 0;
        // a new DecimalFormat each time: the class is not thread safe
        DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00", SYMBOLS);
        return "$" + format.format(amount);
    }
}
