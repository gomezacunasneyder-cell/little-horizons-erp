package com.littlehorizons.erp.controller.finance;

import com.littlehorizons.erp.model.entity.PaymentConcept;
import com.littlehorizons.erp.model.entity.StudentConceptPrice;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure calculations over concepts and prices written with Streams and lambdas (no database, no
 * console): the sorted catalog with its totals, and the price list of a student.
 */
public final class ConceptAnalyzer {

    private static final Collator SPANISH = Collator.getInstance(Locale.forLanguageTag("es-CO"));

    private ConceptAnalyzer() {
    }

    /** Active concepts first (Spanish alphabetical order), then the inactive ones. */
    public static ConceptCatalog catalog(List<PaymentConcept> concepts, Map<Integer, Integer> personalizedByConcept) {
        List<ConceptCatalogEntry> entries = concepts.stream()
                .sorted(Comparator.comparing((PaymentConcept c) -> !c.isActive())
                        .thenComparing(PaymentConcept::getName, SPANISH))
                .map(c -> new ConceptCatalogEntry(c, personalizedByConcept.getOrDefault(c.getId(), 0)))
                .toList();
        long active = concepts.stream().filter(PaymentConcept::isActive).count();
        return new ConceptCatalog(entries, new ConceptSummary(concepts.size(), (int) active,
                concepts.size() - (int) active, (int) concepts.stream().filter(PaymentConcept::isRecurring).count()));
    }

    /** The price of one concept for one student: his personalized value if he has one, the base value if not. */
    public static PriceLine lineFor(PaymentConcept concept, Optional<StudentConceptPrice> personalized) {
        return personalized
                .map(p -> new PriceLine(concept, p.getAmount(), p.getReason()))
                .orElseGet(() -> new PriceLine(concept, concept.getBaseAmount(), null));
    }

    /** One line for each ACTIVE concept, in alphabetical order, applying the student's personalized values. */
    public static List<PriceLine> priceList(List<PaymentConcept> concepts, List<StudentConceptPrice> personalized) {
        Map<Integer, StudentConceptPrice> byConcept = personalized.stream()
                .collect(Collectors.toMap(p -> p.getConcept().getId(), Function.identity()));
        return concepts.stream()
                .filter(PaymentConcept::isActive)
                .sorted(Comparator.comparing(PaymentConcept::getName, SPANISH))
                .map(c -> lineFor(c, Optional.ofNullable(byConcept.get(c.getId()))))
                .toList();
    }
}
