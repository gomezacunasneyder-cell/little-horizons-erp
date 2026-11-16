package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.Guardian;
import com.littlehorizons.erp.model.entity.Invoice;
import com.littlehorizons.erp.model.entity.InvoiceLine;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Builds the text of the electronic invoice: the lines of the .txt file. It is a pure function (same
 * invoice and profile, same lines), so it is easy to test and the file can be regenerated at any time
 * with exactly the same content. Width: 78 characters, for a monospaced font (Notepad, the console).
 */
public final class InvoiceTextRenderer {

    static final int WIDTH = 78;
    private static final int DESC = 40;
    private static final int QTY = 5;
    private static final int UNIT = 15;
    private static final int SUBTOTAL = 15; // 40 + 1 + 5 + 1 + 15 + 1 + 15 = 78 (one line is at most $50.000.000,00: 14 characters)
    private static final int TOTAL = 18; // the totals add up to 30 lines: up to $1.500.000.000,00
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private InvoiceTextRenderer() {
    }

    public static List<String> render(Invoice invoice, BillingProfile profile) {
        List<String> out = new ArrayList<>();
        String heavy = "=".repeat(WIDTH);
        String light = "-".repeat(WIDTH);

        out.add(heavy);
        out.add(centered(BillingProfile.SCHOOL_NAME));
        out.add(centered("NIT " + profile.nitFormatted()));
        out.add(centered(profile.address()));
        out.add(centered("Tel. " + profile.phone() + "  |  " + profile.email()));
        out.add(heavy);
        out.add(centered("FACTURA ELECTRÓNICA DE VENTA"));
        out.add(centered("No. " + invoice.getNumber()));
        out.add(centered("(DOCUMENTO SIMULADO - SIN VALIDEZ ANTE LA DIAN)"));
        out.add(heavy);
        out.add(field("Fecha de emisión", invoice.getIssuedAt().format(STAMP) + " (hora Colombia, UTC" + CufeGenerator.TIME_ZONE + ")"));
        out.add(field("Forma de pago", "Contado (pagos ya recibidos)"));
        out.add(field("Moneda", "COP - Peso colombiano"));
        out.add(light);

        Guardian client = invoice.getClient();
        out.add("CLIENTE (ADQUIRENTE)");
        out.add(field("Nombre", client.getFullName()));
        out.add(field("Documento", client.getDocumentType().getDbLabel() + " " + client.getDocumentNumber()));
        out.add(field("Dirección", client.getAddress()));
        if (client.getPhone() != null) {
            out.add(field("Teléfono", client.getPhone()));
        }
        if (client.getEmail() != null) {
            out.add(field("Correo", client.getEmail()));
        }
        out.add(field("Estudiante", invoice.getStudent().getFullName() + " (" + invoice.getStudent().getDocumentType().getDbLabel()
                + " " + invoice.getStudent().getDocumentNumber() + ")"));
        out.add(light);

        out.add("DETALLE");
        out.add(pad("Descripción", DESC) + " " + padLeft("Cant.", QTY) + " " + padLeft("Valor unitario", UNIT) + " "
                + padLeft("Subtotal", SUBTOTAL));
        out.add(light);
        for (InvoiceLine line : invoice.getLines()) {
            List<String> description = wrap(line.getDescription(), DESC);
            out.add(pad(description.get(0), DESC) + " " + padLeft(String.valueOf(line.getQuantity()), QTY) + " "
                    + padLeft(Money.format(line.getUnitValue()), UNIT) + " " + padLeft(Money.format(line.getSubtotal()), SUBTOTAL));
            description.stream().skip(1).forEach(more -> out.add(more));
            out.add("  Recibo " + line.getReceiptNumber() + " | IVA " + line.getVatPercent() + "%");
        }
        out.add(light);

        out.add(total("Subtotal (sin IVA)", invoice.getSubtotal()));
        vatByRate(invoice).forEach((percent, amount) -> out.add(total("IVA " + percent + "%", amount)));
        out.add(total("TOTAL A PAGAR (COP)", invoice.getTotal()));
        out.add(heavy);

        out.add("ELEMENTOS DE CUMPLIMIENTO DIAN (SIMULADOS)");
        out.add("CUFE (SHA-384):");
        out.add("  " + invoice.getCufe().substring(0, 48));
        out.add("  " + invoice.getCufe().substring(48));
        out.add(field("Ambiente", BillingProfile.ENVIRONMENT + " - Habilitación (pruebas)"));
        out.add("Resolución de habilitación de facturación:");
        wrap(invoice.getResolution(), WIDTH - 2).forEach(part -> out.add("  " + part));
        out.add(light);

        List<String> content = SimulatedQr.content(invoice, profile);
        out.add("CÓDIGO QR (SIMULADO) - contenido que llevaría:");
        content.forEach(part -> wrap(part, WIDTH - 2).forEach(piece -> out.add("  " + piece)));
        out.add("");
        SimulatedQr.draw(content).forEach(row -> out.add(centered(row)));
        out.add("");
        out.add(heavy);
        out.add(centered("Documento académico: no es una factura real ni se envía a la DIAN."));
        out.add(heavy);
        return out;
    }

    /** VAT per rate (0%, 19%...), in ascending order, so that a 19% line and a 0% line are shown separately. */
    static Map<Integer, BigDecimal> vatByRate(Invoice invoice) {
        return invoice.getLines().stream().collect(Collectors.groupingBy(InvoiceLine::getVatPercent, TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, InvoiceLine::getVatAmount, BigDecimal::add)));
    }

    private static String total(String label, BigDecimal amount) {
        return padLeft(label + ":", WIDTH - TOTAL - 1) + " " + padLeft(Money.format(amount), TOTAL);
    }

    private static String field(String label, String value) {
        return pad(label, 17) + ": " + (value == null || value.isBlank() ? "(no registrada)" : value);
    }

    private static String centered(String text) {
        int space = Math.max(0, (WIDTH - text.length()) / 2);
        return " ".repeat(space) + text;
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    /** Cuts the text at spaces so no line is longer than the width (a word longer than the width is cut). */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > width) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (current.length() > 0 && current.length() + 1 + word.length() > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(current.length() == 0 ? "" : " ").append(word);
        }
        lines.add(current.toString());
        return lines;
    }
}
