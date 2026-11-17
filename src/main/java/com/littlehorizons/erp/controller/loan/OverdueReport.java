package com.littlehorizons.erp.controller.loan;

import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.enums.BorrowerType;
import com.littlehorizons.erp.model.enums.ResourceCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The loans not returned by their scheduled date, calculated as of {@code asOf}: the most delayed
 * first, with the totals the library or the laboratory needs to organize the follow-up. Both maps
 * contain EVERY type / category, with 0 when none.
 */
public record OverdueReport(LocalDate asOf, List<Loan> loans, Map<BorrowerType, Long> byBorrowerType,
                            Map<ResourceCategory, Long> byCategory, long maxDaysLate) {

    public int total() {
        return loans.size();
    }
}
