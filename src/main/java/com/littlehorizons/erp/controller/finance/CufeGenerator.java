package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.Invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * The CUFE (Código Único de Factura Electrónica), SIMULATED. The real one is the SHA-384 hash of the
 * invoice data joined in a fixed order; this class does the same with the same fields: number,
 * date, time, subtotal, VAT (code 01), other taxes (04 and 03, always zero here), total, NIT of the
 * school, document of the client, technical key and environment. Same data, same CUFE; change one
 * cent and the CUFE changes. It is NOT valid before the DIAN: the technical key is invented.
 */
public final class CufeGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** Colombia has no daylight saving time, so the offset is always -05:00. */
    static final String TIME_ZONE = "-05:00";

    private CufeGenerator() {
    }

    /** The text that is hashed (the invoice must already have its number). */
    static String source(Invoice invoice, BillingProfile profile) {
        return invoice.getNumber()
                + invoice.getIssuedAt().format(DATE)
                + invoice.getIssuedAt().format(TIME) + TIME_ZONE
                + plain(invoice.getSubtotal())
                + "01" + plain(invoice.getVatTotal())
                + "04" + plain(BigDecimal.ZERO)
                + "03" + plain(BigDecimal.ZERO)
                + plain(invoice.getTotal())
                + profile.nitDigits()
                + invoice.getClient().getDocumentNumber()
                + profile.technicalKey()
                + BillingProfile.ENVIRONMENT;
    }

    /** SHA-384 in lowercase hexadecimal: always 96 characters. */
    public static String generate(Invoice invoice, BillingProfile profile) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-384").digest(source(invoice, profile).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // every Java platform must provide SHA-384, so this cannot happen
            throw new IllegalStateException("SHA-384 is not available", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
