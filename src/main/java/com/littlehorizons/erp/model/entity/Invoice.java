package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An electronic invoice (simulated): issued to ONE guardian (the client) for the payments of ONE
 * student. Number, CUFE and resolution are NOT chosen by the user: they are given by the system
 * when the invoice is saved (see InvoiceDao). The totals are always calculated from the lines.
 */
public class Invoice {

    public static final String PREFIX = "FE";
    public static final int MAX_LINES = 30;
    private static final Pattern NUMBER = Pattern.compile("FE-\\d{6}");

    private Integer id;
    private final Student student;
    private final Guardian client;
    private final LocalDateTime issuedAt;
    private final List<InvoiceLine> lines;
    private int sequence;
    private String cufe;
    private String resolution;

    public Invoice(Student student, Guardian client, LocalDateTime issuedAt, List<InvoiceLine> lines) {
        this.student = Objects.requireNonNull(student, "student");
        this.client = Objects.requireNonNull(client, "client");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt").truncatedTo(ChronoUnit.SECONDS);
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("La factura necesita al menos un pago.");
        }
        if (lines.size() > MAX_LINES) {
            throw new ValidationException("Una factura admite máximo " + MAX_LINES + " líneas.");
        }
        if (lines.stream().map(InvoiceLine::getPaymentId).distinct().count() != lines.size()) {
            throw new ValidationException("Un pago no puede aparecer dos veces en la misma factura.");
        }
        this.lines = List.copyOf(lines);
    }

    /** FE-000123 for the sequence 123. */
    public static String numberOf(int sequence) {
        return String.format("%s-%06d", PREFIX, sequence);
    }

    /** Accepts "123" or "fe-000123" and returns "FE-000123"; anything else is a validation error. */
    public static String normalizeNumber(String text) {
        String clean = text == null ? "" : text.trim().toUpperCase();
        if (clean.matches("\\d{1,6}") && Integer.parseInt(clean) > 0) {
            return numberOf(Integer.parseInt(clean));
        }
        if (NUMBER.matcher(clean).matches() && !clean.equals(numberOf(0))) {
            return clean;
        }
        throw new ValidationException("Escriba el número de factura, por ejemplo 15 o FE-000015.");
    }

    /** The consecutive inside "FE-000123" (123). */
    public static int sequenceOf(String number) {
        return Integer.parseInt(normalizeNumber(number).substring(PREFIX.length() + 1));
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    /** The guardian the invoice is issued to. */
    public Guardian getClient() {
        return client;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public List<InvoiceLine> getLines() {
        return lines;
    }

    /** The consecutive number (0 until the invoice is saved). */
    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    /** FE-000123, or null until the invoice is saved. */
    public String getNumber() {
        return sequence == 0 ? null : numberOf(sequence);
    }

    /** factura_000123.txt: unique because the consecutive number is unique. */
    public String getFileName() {
        return sequence == 0 ? null : String.format("factura_%06d.txt", sequence);
    }

    public String getCufe() {
        return cufe;
    }

    public void setCufe(String cufe) {
        this.cufe = cufe;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    /** Sum of the values without VAT. */
    public BigDecimal getSubtotal() {
        return lines.stream().map(InvoiceLine::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getVatTotal() {
        return lines.stream().map(InvoiceLine::getVatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What was paid: subtotal plus VAT. */
    public BigDecimal getTotal() {
        return getSubtotal().add(getVatTotal());
    }
}
