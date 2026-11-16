package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.dao.PaidInstallment;
import com.littlehorizons.erp.model.entity.PaymentConcept;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** A recurring concept with everything needed to know who is late: personalized values and payments of the year. */
public record ConceptInstallments(PaymentConcept concept, Map<Integer, BigDecimal> personalizedByStudent,
                                  List<PaidInstallment> paid) {
}
