package com.littlehorizons.erp.controller.loan;

import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.enums.LoanStatus;

import java.util.List;
import java.util.Map;

/** A list of loans with how many there are in each state (the map contains EVERY state, with 0 when none). */
public record LoanListing(List<Loan> loans, Map<LoanStatus, Long> byStatus) {

    public int total() {
        return loans.size();
    }
}
