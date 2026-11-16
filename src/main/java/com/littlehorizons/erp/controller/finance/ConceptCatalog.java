package com.littlehorizons.erp.controller.finance;

import java.util.List;

/** The catalog ready for the view: active concepts first, then the inactive ones, plus the totals. */
public record ConceptCatalog(List<ConceptCatalogEntry> entries, ConceptSummary summary) {
}
