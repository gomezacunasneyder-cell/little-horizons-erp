package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.PaymentConceptController;
import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.entity.Person;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.util.Money;

import java.math.BigDecimal;

/** Asks the data of a new payment concept, or the changes of an existing one, until every answer is valid. */
final class ConceptForm {

    private final ConsoleInput input;
    private final PaymentConceptController controller;

    ConceptForm(ConsoleInput input, PaymentConceptController controller) {
        this.input = input;
        this.controller = controller;
    }

    PaymentConcept readNew() {
        String code = readCode("Código (letras, números y guion bajo, ejemplo PENSION_MENSUAL): ", null);
        String name = readName("Nombre del concepto (solo letras, ejemplo: Pensión mensual): ", null);
        String description = input.read("Descripción (Enter para omitir): ", PaymentConcept::checkDescription);
        BigDecimal amount = input.read("Valor base en pesos, sin puntos (ejemplo 650000): ",
                text -> PaymentConcept.checkAmount(Money.parse(text)));
        int vat = input.readInt("IVA que se factura, de 0 a 100 (0 en servicios educativos): ", 0, 100);
        boolean recurring = input.readYesNo("¿Es un cobro recurrente (por ejemplo mensual)?");
        return new PaymentConcept(code, name, description, amount, vat, recurring);
    }

    /**
     * Returns a NEW PaymentConcept with the changes (same id and state). The original is not touched,
     * so if the user cancels in the middle or the controller rejects the change, nothing is half-edited.
     */
    PaymentConcept readChanges(PaymentConcept current) {
        Integer id = current.getId();
        String code = input.read("Código [" + current.getCode() + "] (Enter = mantener): ", text -> text.isEmpty()
                ? current.getCode() : checkFreeCode(text, id));
        String name = input.read("Nombre [" + current.getName() + "] (Enter = mantener): ", text -> text.isEmpty()
                ? current.getName() : checkFreeName(text, id));
        String description = input.readYesNo("¿Cambiar la descripción?")
                ? input.read("Nueva descripción (Enter para dejarla vacía): ", PaymentConcept::checkDescription)
                : current.getDescription();
        BigDecimal amount = input.read("Valor base [" + Money.format(current.getBaseAmount()) + "] (Enter = mantener): ",
                text -> text.isEmpty() ? current.getBaseAmount() : PaymentConcept.checkAmount(Money.parse(text)));
        int vat = input.read("IVA [" + current.getVatPercent() + "] (Enter = mantener): ", text -> {
            if (text.isEmpty()) {
                return current.getVatPercent();
            }
            int value = Integer.parseInt(text);
            if (value < 0 || value > 100) {
                throw new ValidationException("Ingrese un número entre 0 y 100.");
            }
            return value;
        });
        boolean recurring = input.readYesNo("¿Es un cobro recurrente? (hoy: " + (current.isRecurring() ? "Sí" : "No") + ")");
        PaymentConcept changed = new PaymentConcept(code, name, description, amount, vat, recurring);
        changed.setId(id);
        changed.setActive(current.isActive());
        return changed;
    }

    private String readCode(String prompt, Integer excludeId) {
        return input.read(prompt, text -> checkFreeCode(text, excludeId));
    }

    private String readName(String prompt, Integer excludeId) {
        return input.read(prompt, text -> checkFreeName(text, excludeId));
    }

    private String checkFreeCode(String text, Integer excludeId) {
        String code = PaymentConcept.checkCode(text);
        if (isTaken(controller.codeTaken(code, excludeId))) {
            throw new ValidationException("Ya existe un concepto con el código " + code + ".");
        }
        return code;
    }

    private String checkFreeName(String text, Integer excludeId) {
        String name = Person.requireLabel(text, PaymentConcept.NAME_MAX, "El nombre del concepto");
        if (isTaken(controller.nameTaken(name, excludeId))) {
            throw new ValidationException("Ya existe un concepto llamado «" + name + "».");
        }
        return name;
    }

    private static boolean isTaken(Result<Boolean> result) {
        return result.isSuccess() && result.getData();
    }
}
