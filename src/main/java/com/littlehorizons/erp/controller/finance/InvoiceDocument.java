package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.Invoice;

import java.nio.file.Path;
import java.util.List;

/** An invoice with its text and, when it could be written, the file where it was saved (null if not). */
public record InvoiceDocument(Invoice invoice, List<String> text, Path file) {
}
