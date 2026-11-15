package com.littlehorizons.erp.controller.club;

/** Totals of the club catalog, shown under the table. */
public record ClubSummary(int totalClubs, int activeClubs, int fullClubs, int activeMemberships, int availableSpots) {
}
