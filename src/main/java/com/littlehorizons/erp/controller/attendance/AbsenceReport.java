package com.littlehorizons.erp.controller.attendance;

import java.time.LocalDate;
import java.util.List;

/** The result of looking for concerning absence patterns between two dates. */
public record AbsenceReport(int weeklyLimit, LocalDate from, LocalDate to, List<AbsenceAlert> alerts) {
}
