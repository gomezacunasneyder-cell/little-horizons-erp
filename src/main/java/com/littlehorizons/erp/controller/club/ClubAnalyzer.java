package com.littlehorizons.erp.controller.club;

import com.littlehorizons.erp.model.entity.Club;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure calculations over clubs, written with Streams and lambdas (no database, no console), so
 * they are easy to test: join each club with its member count, sort the catalog, and total it.
 */
public final class ClubAnalyzer {

    /** Compares texts with Spanish rules, so «Fútbol» is placed with the F and not after the Z. */
    private static final Collator SPANISH = Collator.getInstance(Locale.forLanguageTag("es-CO"));

    private ClubAnalyzer() {
    }

    /** Active clubs first, then the closed ones; inside each group alphabetical by name. */
    public static List<ClubOccupancy> occupancy(List<Club> clubs, Map<Integer, Integer> activeMembersByClub) {
        return clubs.stream()
                .map(club -> new ClubOccupancy(club, activeMembersByClub.getOrDefault(club.getId(), 0)))
                .sorted(Comparator.comparing((ClubOccupancy o) -> !o.club().isActive())
                        .thenComparing(o -> o.club().getName(), SPANISH))
                .toList();
    }

    /** Totals of the catalog. Only the active clubs count for "full" and "available spots". */
    public static ClubSummary summarize(List<ClubOccupancy> catalog) {
        List<ClubOccupancy> open = catalog.stream().filter(o -> o.club().isActive()).toList();
        return new ClubSummary(
                catalog.size(),
                open.size(),
                (int) open.stream().filter(ClubOccupancy::isFull).count(),
                open.stream().mapToInt(ClubOccupancy::activeMembers).sum(),
                open.stream().mapToInt(ClubOccupancy::availableSpots).sum());
    }

    public static ClubCatalog catalog(List<Club> clubs, Map<Integer, Integer> activeMembersByClub) {
        List<ClubOccupancy> occupancy = occupancy(clubs, activeMembersByClub);
        return new ClubCatalog(occupancy, summarize(occupancy));
    }
}
