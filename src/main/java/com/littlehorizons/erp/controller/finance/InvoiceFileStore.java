package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.Invoice;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Saves the text of an invoice as a .txt file (UTF-8, one file per invoice, named after its unique
 * consecutive number: factura_000123.txt). Writing the same invoice again replaces the file with
 * the same content, so a lost or damaged file can always be regenerated from the database.
 */
public final class InvoiceFileStore {

    public static final String DEFAULT_DIRECTORY = "invoices";

    private final Path directory;

    public InvoiceFileStore(Path directory) {
        this.directory = directory;
    }

    /** Where the file of this invoice is (or would be) saved. */
    public Path pathOf(Invoice invoice) {
        return directory.resolve(invoice.getFileName());
    }

    /** Creates the folder if needed and writes the lines. The writer is closed even if writing fails. */
    public Path write(Invoice invoice, List<String> lines) throws IOException {
        Files.createDirectories(directory);
        Path file = pathOf(invoice);
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        return file;
    }
}
