package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.finance.ConceptCatalog;
import com.littlehorizons.erp.controller.finance.ConceptCatalogEntry;
import com.littlehorizons.erp.controller.finance.ConceptSummary;
import com.littlehorizons.erp.controller.finance.PriceLine;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.util.Money;

import java.util.List;

/** Prints the catalog and the prices of a student as tables. It only formats; every number comes already calculated. */
final class ConceptPrinter {

    private final ConsoleOutput output;

    ConceptPrinter(ConsoleOutput output) {
        this.output = output;
    }

    void catalog(ConceptCatalog catalog) {
        output.table(List.of("Código", "Concepto", "Valor base", "IVA", "Recurrente", "Estado", "Valores personalizados"),
                catalog.entries().stream().map(this::catalogRow).toList());
        ConceptSummary s = catalog.summary();
        output.info("Conceptos: " + s.total() + " (" + s.active() + " activos, " + s.inactive() + " inactivos, "
                + s.recurring() + " recurrentes).");
    }

    private List<String> catalogRow(ConceptCatalogEntry entry) {
        PaymentConcept c = entry.concept();
        return List.of(c.getCode(), c.getName(), Money.format(c.getBaseAmount()), c.getVatPercent() + "%",
                c.isRecurring() ? "Sí" : "No", c.isActive() ? "Activo" : "Inactivo",
                String.valueOf(entry.personalizedStudents()));
    }

    void prices(List<PriceLine> lines) {
        output.table(List.of("Concepto", "Valor base", "Valor que paga", "Descuento", "Motivo"),
                lines.stream().map(this::priceRow).toList());
        long personalized = lines.stream().filter(PriceLine::isPersonalized).count();
        output.info(lines.size() + " concepto(s) activos; " + personalized + " con valor personalizado.");
    }

    private List<String> priceRow(PriceLine line) {
        return List.of(line.concept().getName(), Money.format(line.baseAmount()), Money.format(line.effectiveAmount()),
                line.isPersonalized() ? line.discountPercent().stripTrailingZeros().toPlainString() + "%" : "-",
                line.isPersonalized() ? line.reason() : "-");
    }

    /** The data of one concept, shown before editing it. */
    void detail(PaymentConcept concept) {
        output.line("  Código:      " + concept.getCode());
        output.line("  Nombre:      " + concept.getName());
        output.line("  Descripción: " + (concept.getDescription() == null ? "-" : concept.getDescription()));
        output.line("  Valor base:  " + Money.format(concept.getBaseAmount()));
        output.line("  IVA:         " + concept.getVatPercent() + "%");
        output.line("  Recurrente:  " + (concept.isRecurring() ? "Sí" : "No"));
    }
}
