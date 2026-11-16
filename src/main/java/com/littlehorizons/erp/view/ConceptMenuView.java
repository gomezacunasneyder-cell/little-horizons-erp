package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.PaymentConceptController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.controller.StudentController;
import com.littlehorizons.erp.controller.StudentPriceController;
import com.littlehorizons.erp.controller.finance.ConceptCatalog;
import com.littlehorizons.erp.controller.finance.PriceLine;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * "Gestión Financiera - Conceptos de pago": the catalog of what the school charges and the
 * personalized values of each student (sibling discounts, scholarships). It only asks for data
 * and prints; the rules (unique codes, active students, values never above the base) are in the
 * controllers and entities.
 */
public final class ConceptMenuView extends AbstractMenuView {

    private static final List<String> OPTIONS = List.of(
            "Ver el catálogo de conceptos",
            "Registrar un concepto",
            "Editar un concepto",
            "Activar o desactivar un concepto",
            "Asignar un valor personalizado a un estudiante (descuento o beca)",
            "Quitar un valor personalizado",
            "Ver lo que paga un estudiante por cada concepto");

    private final PaymentConceptController concepts;
    private final StudentPriceController prices;
    private final ConceptSelector conceptSelector;
    private final ConceptSelector activeConceptSelector;
    private final StudentSelector studentSelector;
    private final ConceptForm form;
    private final ConceptPrinter conceptPrinter;
    private final ResultPrinter printer;

    public ConceptMenuView(ConsoleInput input, ConsoleOutput output, PaymentConceptController concepts,
                           StudentPriceController prices, StudentController students) {
        super(input, output);
        this.concepts = concepts;
        this.prices = prices;
        this.conceptSelector = new ConceptSelector(input, output, concepts::search);
        this.activeConceptSelector = new ConceptSelector(input, output, concepts::searchActive);
        this.studentSelector = new StudentSelector(input, output, students);
        this.form = new ConceptForm(input, concepts);
        this.conceptPrinter = new ConceptPrinter(output);
        this.printer = new ResultPrinter(output);
    }

    @Override
    protected String title() {
        return "GESTIÓN FINANCIERA - CONCEPTOS DE PAGO";
    }

    @Override
    protected List<String> options() {
        return OPTIONS;
    }

    @Override
    protected void handle(int option) {
        switch (option) {
            case 1 -> showCatalog();
            case 2 -> createConcept();
            case 3 -> editConcept();
            case 4 -> toggleConcept();
            case 5 -> assignPrice();
            case 6 -> removePrice();
            case 7 -> showStudentPrices();
            default -> throw new IllegalStateException("Unexpected option " + option);
        }
    }

    private void showCatalog() {
        Result<ConceptCatalog> result = concepts.catalog();
        if (result.isSuccess()) {
            conceptPrinter.catalog(result.getData());
        } else {
            output.error(result.getMessage());
        }
    }

    private void createConcept() {
        printer.report(concepts.create(form.readNew()));
    }

    private void editConcept() {
        PaymentConcept concept = conceptSelector.select();
        output.info("Datos actuales:");
        conceptPrinter.detail(concept);
        printer.report(concepts.update(form.readChanges(concept)));
    }

    private void toggleConcept() {
        PaymentConcept concept = conceptSelector.select();
        if (concept.isActive()) {
            if (input.readYesNo("¿Desactivar «" + concept.getName() + "»? Ya no se podrá cobrar ni personalizar")) {
                printer.report(concepts.setActive(concept, false));
            }
        } else if (input.readYesNo("El concepto está inactivo. ¿Activarlo?")) {
            printer.report(concepts.setActive(concept, true));
        }
    }

    private void assignPrice() {
        Student student = studentSelector.select();
        PaymentConcept concept = activeConceptSelector.select();
        int year = input.readAcademicYear();
        Result<PriceLine> current = prices.priceOf(student, concept, year);
        if (current.isSuccess()) {
            output.info("Hoy " + student.getFullName() + " paga " + Money.format(current.getData().effectiveAmount())
                    + " por «" + concept.getName() + "» en " + year + " (valor base " + Money.format(concept.getBaseAmount()) + ").");
        }
        BigDecimal amount = input.read("Nuevo valor que pagará, en pesos y sin puntos (0 = beca total): ",
                text -> StudentConceptPrice.checkAmount(Money.parse(text), concept));
        String reason = input.read("Motivo (ejemplo: descuento por hermanos): ", StudentConceptPrice::checkReason);
        printer.report(prices.assign(new StudentConceptPrice(student, concept, year, amount, reason)));
    }

    private void removePrice() {
        Student student = studentSelector.select();
        PaymentConcept concept = conceptSelector.select();
        int year = input.readAcademicYear();
        if (input.readYesNo("¿Quitar el valor personalizado de " + student.getFullName() + " para «"
                + concept.getName() + "» en " + year + "?")) {
            printer.report(prices.remove(student, concept, year));
        } else {
            output.info("No se quitó nada.");
        }
    }

    private void showStudentPrices() {
        Student student = studentSelector.select();
        int year = input.readAcademicYear();
        Result<List<PriceLine>> result = prices.priceList(student, year);
        if (!result.isSuccess()) {
            output.error(result.getMessage());
        } else {
            output.info("Valores de " + student.getFullName() + " en " + year);
            conceptPrinter.prices(result.getData());
        }
    }
}
