package com.littlehorizons.erp.controller.club;

import java.util.List;

/** The catalog of clubs with their occupancy and the totals, ready for the view to print. */
public record ClubCatalog(List<ClubOccupancy> clubs, ClubSummary summary) {
}
