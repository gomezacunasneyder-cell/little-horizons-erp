package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.Invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * The QR code of the invoice, SIMULATED. It has two parts: the content that a real DIAN QR would
 * carry (number, date, NIT, client, values, CUFE, verification link), and a drawing made of text
 * blocks. The drawing looks like a QR (three corner squares, a timing line and a pattern of
 * modules) and is built from a hash of the content, so the same invoice always draws the same
 * picture. It is NOT scannable: a real QR needs an encoder library, which this project does not use.
 */
public final class SimulatedQr {

    private static final int SIZE = 25;
    private static final String DARK = "██";
    private static final String LIGHT = "  ";
    /** DIAN's public test environment page; with an invented key it only shows how the link would look. */
    private static final String VERIFY_URL = "https://catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey=";

    private SimulatedQr() {
    }

    /** The lines of text the QR "contains". */
    public static List<String> content(Invoice invoice, BillingProfile profile) {
        return List.of(
                "NumFac: " + invoice.getNumber(),
                "FecFac: " + invoice.getIssuedAt().toLocalDate(),
                "HorFac: " + invoice.getIssuedAt().toLocalTime() + CufeGenerator.TIME_ZONE,
                "NitFac: " + profile.nitDigits(),
                "DocAdq: " + invoice.getClient().getDocumentNumber(),
                "ValFac: " + plain(invoice.getSubtotal()),
                "ValIva: " + plain(invoice.getVatTotal()),
                "ValOtroIm: 0.00",
                "ValTolFac: " + plain(invoice.getTotal()),
                "CUFE: " + invoice.getCufe(),
                "QRCode: " + VERIFY_URL + invoice.getCufe());
    }

    /** The picture: 25 rows of text, each module two characters wide so it looks square. */
    public static List<String> draw(List<String> content) {
        boolean[][] modules = new boolean[SIZE][SIZE];
        byte[] bits = hash(String.join("\n", content));
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int index = row * SIZE + col;
                modules[row][col] = (bits[(index / 8) % bits.length] >> (index % 8) & 1) == 1;
            }
        }
        for (int i = 8; i < SIZE - 8; i++) { // timing line between the corner squares
            modules[6][i] = i % 2 == 0;
            modules[i][6] = i % 2 == 0;
        }
        finder(modules, 0, 0);
        finder(modules, 0, SIZE - 7);
        finder(modules, SIZE - 7, 0);
        List<String> rows = new ArrayList<>();
        for (boolean[] row : modules) {
            StringBuilder line = new StringBuilder();
            for (boolean dark : row) {
                line.append(dark ? DARK : LIGHT);
            }
            rows.add(line.toString());
        }
        return rows;
    }

    /** A 7x7 corner square (dark ring, light ring, dark centre) with a light margin around it. */
    private static void finder(boolean[][] modules, int top, int left) {
        for (int r = -1; r <= 7; r++) {
            for (int c = -1; c <= 7; c++) {
                int row = top + r;
                int col = left + c;
                if (row < 0 || col < 0 || row >= SIZE || col >= SIZE) {
                    continue;
                }
                boolean inside = r >= 0 && r <= 6 && c >= 0 && c <= 6;
                boolean ring = r == 0 || r == 6 || c == 0 || c == 6;
                boolean centre = r >= 2 && r <= 4 && c >= 2 && c <= 4;
                modules[row][col] = inside && (ring || centre);
            }
        }
    }

    /** 256 bits that depend only on the content: a SHA-256 hash repeated with a counter. */
    private static byte[] hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] all = new byte[SIZE * SIZE / 8 + 32];
            for (int block = 0; block * 32 < all.length; block++) {
                digest.reset();
                byte[] part = digest.digest((text + "#" + block).getBytes(StandardCharsets.UTF_8));
                System.arraycopy(part, 0, all, block * 32, Math.min(32, all.length - block * 32));
            }
            return all;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
