package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.PaymentConcept;

/** A concept of the catalog with the number of students that have a personalized value for it. */
public record ConceptCatalogEntry(PaymentConcept concept, int personalizedStudents) {
}
