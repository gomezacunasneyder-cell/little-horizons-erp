package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.exception.ConfigurationException;
import com.littlehorizons.erp.model.entity.Invoice;
import com.littlehorizons.erp.util.AppConfig;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * The data of the school that goes on every invoice, and the (simulated) DIAN authorization. All of
 * it is fictional. Each value can be changed with a "billing.*" key of config.txt; without the key
 * the default below is used (see config.example.txt).
 */
public record BillingProfile(String nit, String address, String phone, String email,
                             String resolutionNumber, LocalDate resolutionDate, int rangeFrom, int rangeTo,
                             String technicalKey) {

    public static final String SCHOOL_NAME = "Colegio Internacional Little Horizons S.A.S.";
    /** The authorization lasts 24 months from its date. */
    public static final int RESOLUTION_VALID_MONTHS = 24;
    /** DIAN environment code: 2 = habilitación (tests). The invoices of this program are never production. */
    public static final String ENVIRONMENT = "2";

    private static final Pattern NIT = Pattern.compile("\\d{9}-\\d");
    private static final Pattern RESOLUTION_NUMBER = Pattern.compile("\\d{10,14}");

    public static BillingProfile fromConfig(AppConfig config) {
        return from(config::optional);
    }

    /** {@code optional} gives the value of a key, or the default received as second argument (as AppConfig.optional does). */
    public static BillingProfile from(BiFunction<String, String, String> optional) {
        String nit = optional.apply("billing.nit", "901234567-8").trim();
        if (!NIT.matcher(nit).matches()) {
            throw new ConfigurationException("billing.nit debe tener 9 dígitos, un guion y el dígito de verificación "
                    + "(ejemplo: 901234567-8), no «" + nit + "».");
        }
        String resolutionNumber = optional.apply("billing.resolution.number", "18760000001").trim();
        if (!RESOLUTION_NUMBER.matcher(resolutionNumber).matches()) {
            throw new ConfigurationException("billing.resolution.number debe tener entre 10 y 14 dígitos, no «"
                    + resolutionNumber + "».");
        }
        int from = integer(optional, "billing.resolution.from", "1");
        int to = integer(optional, "billing.resolution.to", "50000");
        if (from < 1 || to < from || to > 999999) {
            throw new ConfigurationException("billing.resolution.from y billing.resolution.to deben ser un rango "
                    + "entre 1 y 999999 (ejemplo: 1 y 50000).");
        }
        return new BillingProfile(nit,
                text(optional, "billing.address", "Carrera 15 # 85-30, Bogotá D.C."),
                text(optional, "billing.phone", "(601) 745 2100"),
                text(optional, "billing.email", "facturacion@littlehorizons.edu.co"),
                resolutionNumber, date(optional, "billing.resolution.date", "2025-12-15"), from, to,
                text(optional, "billing.technical.key", "fc8eab76-ce3a-4b5c-9d41-2e6a1d7f0b93"));
    }

    /** The NIT without the verification digit, as the CUFE uses it: 901234567. */
    public String nitDigits() {
        return nit.substring(0, nit.indexOf('-'));
    }

    /** The NIT written for people: 901.234.567-8. */
    public String nitFormatted() {
        String d = nitDigits();
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6) + nit.substring(nit.indexOf('-'));
    }

    public LocalDate resolutionValidUntil() {
        return resolutionDate.plusMonths(RESOLUTION_VALID_MONTHS);
    }

    /** The text saved with each invoice and printed on it. */
    public String resolutionText() {
        return "Resolución DIAN No. " + resolutionNumber + " del " + resolutionDate + ", prefijo " + Invoice.PREFIX
                + ", rango " + number(rangeFrom) + " a " + number(rangeTo) + ", vigente hasta " + resolutionValidUntil()
                + " (SIMULADA)";
    }

    private static String number(int sequence) {
        return Invoice.numberOf(sequence);
    }

    private static String text(BiFunction<String, String, String> optional, String key, String defaultValue) {
        String value = optional.apply(key, defaultValue).trim();
        if (value.isEmpty()) {
            throw new ConfigurationException(key + " no puede estar vacío.");
        }
        return value;
    }

    private static int integer(BiFunction<String, String, String> optional, String key, String defaultValue) {
        String value = optional.apply(key, defaultValue).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConfigurationException(key + " debe ser un número entero, no «" + value + "».");
        }
    }

    private static LocalDate date(BiFunction<String, String, String> optional, String key, String defaultValue) {
        String value = optional.apply(key, defaultValue).trim();
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException(key + " debe ser una fecha aaaa-mm-dd (ejemplo: 2025-12-15), no «" + value + "».");
        }
    }
}
