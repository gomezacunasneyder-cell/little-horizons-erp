package com.littlehorizons.erp.view;

import com.littlehorizons.erp.controller.Result;
import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.util.Money;

import java.util.List;
import java.util.function.Function;

/** Dialog to find and pick one payment concept; the generic work is done by EntitySelector. */
final class ConceptSelector {

    private final EntitySelector<PaymentConcept> selector;

    /** {@code search} decides which concepts can be found (all of them, or only the active ones). */
    ConceptSelector(ConsoleInput input, ConsoleOutput output, Function<String, Result<List<PaymentConcept>>> search) {
        this.selector = new EntitySelector<>(input, output, "Código o nombre del concepto: ", search,
                c -> c.getCode() + " - " + c.getName() + " (" + Money.format(c.getBaseAmount()) + ")"
                        + (c.isActive() ? "" : " [inactivo]"));
    }

    PaymentConcept select() {
        return selector.select();
    }
}
