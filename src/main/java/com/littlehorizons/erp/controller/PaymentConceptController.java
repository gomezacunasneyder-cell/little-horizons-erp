package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.finance.ConceptAnalyzer;
import com.littlehorizons.erp.controller.finance.ConceptCatalog;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.PaymentConceptDao;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.util.ErrorLogger;
import com.littlehorizons.erp.util.Money;

import java.util.List;

/**
 * Rules of the payment concept catalog: the code and the name are unique, and a concept is never
 * deleted (payments refer to it), only deactivated so it cannot be charged again.
 */
public class PaymentConceptController extends BaseController {

    private final PaymentConceptDao conceptDao;

    public PaymentConceptController(PaymentConceptDao conceptDao, ErrorLogger logger) {
        super(logger);
        this.conceptDao = conceptDao;
    }

    public Result<PaymentConcept> create(PaymentConcept concept) {
        return execute("Create payment concept", () -> {
            requireFreeCodeAndName(concept);
            conceptDao.create(concept);
            return Result.ok(concept, "Concepto «" + concept.getName() + "» creado con valor base "
                    + Money.format(concept.getBaseAmount()) + ".");
        });
    }

    /** Saves the edited data of an existing concept (the object carries its id and its current active flag). */
    public Result<PaymentConcept> update(PaymentConcept concept) {
        return execute("Update payment concept", () -> {
            requireFreeCodeAndName(concept);
            conceptDao.update(concept);
            return Result.ok(concept, "Concepto «" + concept.getName() + "» actualizado.");
        });
    }

    /** Activates or deactivates a concept. */
    public Result<PaymentConcept> setActive(PaymentConcept concept, boolean active) {
        return execute(active ? "Activate payment concept" : "Deactivate payment concept", () -> {
            if (concept.isActive() == active) {
                throw new ValidationException("El concepto ya está " + (active ? "activo." : "inactivo."));
            }
            concept.setActive(active);
            try {
                conceptDao.update(concept);
            } catch (RuntimeException | DaoException e) {
                concept.setActive(!active); // the database kept the old value, so the object must too
                throw e;
            }
            return Result.ok(concept, "Concepto «" + concept.getName() + "» " + (active ? "activado." : "desactivado."));
        });
    }

    /** The catalog: active concepts first, with the number of students that have a personalized value. */
    public Result<ConceptCatalog> catalog() {
        return execute("Show payment concept catalog", () -> Result.ok(
                ConceptAnalyzer.catalog(conceptDao.findAll(), conceptDao.countPersonalizedByConcept()), ""));
    }

    public Result<List<PaymentConcept>> search(String term) {
        return execute("Search payment concepts", () -> Result.ok(conceptDao.search(requireTerm(term)), ""));
    }

    /** Same search but only the active concepts (the ones that can still be charged). */
    public Result<List<PaymentConcept>> searchActive(String term) {
        return execute("Search active payment concepts", () -> Result.ok(
                conceptDao.search(requireTerm(term)).stream().filter(PaymentConcept::isActive).toList(), ""));
    }

    public Result<Boolean> codeTaken(String code, Integer excludeId) {
        return execute("Check concept code", () -> Result.ok(conceptDao.codeExists(code.trim().toUpperCase(), excludeId), ""));
    }

    public Result<Boolean> nameTaken(String name, Integer excludeId) {
        return execute("Check concept name", () -> Result.ok(conceptDao.nameExists(name.trim(), excludeId), ""));
    }

    private static String requireTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new ValidationException("Escriba parte del código o del nombre del concepto.");
        }
        return term.trim();
    }

    private void requireFreeCodeAndName(PaymentConcept concept) throws DaoException {
        if (conceptDao.codeExists(concept.getCode(), concept.getId())) {
            throw new ValidationException("Ya existe un concepto con el código " + concept.getCode() + ".");
        }
        if (conceptDao.nameExists(concept.getName(), concept.getId())) {
            throw new ValidationException("Ya existe un concepto llamado «" + concept.getName() + "».");
        }
    }
}
