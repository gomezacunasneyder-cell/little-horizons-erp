package com.littlehorizons.erp.controller.loan;

import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.enums.BorrowerType;
import com.littlehorizons.erp.model.enums.LoanStatus;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.util.EnumCounts;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Pure calculations over lists of loans (no database): ordering and totals, done with lambdas and streams. */
public final class LoanAnalyzer {

    private LoanAnalyzer() {
    }

    public static LoanListing listing(List<Loan> loans) {
        return new LoanListing(loans, EnumCounts.countBy(LoanStatus.class, loans, Loan::getStatus));
    }

    /** The most delayed loan first; when two have the same delay, the one with the earlier loan date. */
    public static OverdueReport overdueReport(List<Loan> overdue, LocalDate asOf) {
        Comparator<Loan> mostDelayedFirst = Comparator.<Loan>comparingLong(loan -> loan.daysLate(asOf)).reversed()
                .thenComparing(Loan::getLoanDate);
        List<Loan> sorted = overdue.stream().sorted(mostDelayedFirst).toList();
        long maxDaysLate = sorted.stream().mapToLong(loan -> loan.daysLate(asOf)).max().orElse(0);
        return new OverdueReport(asOf, sorted,
                EnumCounts.countBy(BorrowerType.class, sorted, Loan::getBorrowerType),
                EnumCounts.countBy(ResourceCategory.class, sorted, loan -> loan.getResource().getCategory()),
                maxDaysLate);
    }
}
