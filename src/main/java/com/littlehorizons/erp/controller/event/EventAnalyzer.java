package com.littlehorizons.erp.controller.event;

import com.littlehorizons.erp.model.entity.EventParticipation;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.enums.EventPosition;
import com.littlehorizons.erp.model.enums.EventType;

import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure calculations over events written with Streams and lambdas (no database, no console), so
 * they are easy to test: the status of each event, the display order of the calendar, the totals,
 * the sorted results of a competition and the history of a student.
 */
public final class EventAnalyzer {

    private static final Collator SPANISH = Collator.getInstance(Locale.forLanguageTag("es-CO"));

    private EventAnalyzer() {
    }

    public static EventStatus statusOf(SchoolEvent event, LocalDate today) {
        if (!event.isActive()) {
            return EventStatus.CANCELLED;
        }
        if (event.getDate().isAfter(today)) {
            return EventStatus.UPCOMING;
        }
        return event.getDate().isEqual(today) ? EventStatus.TODAY : EventStatus.HELD;
    }

    /**
     * Display order: what is coming first (nearest date first), then what already happened (most
     * recent first), and the cancelled ones at the end.
     */
    public static EventCalendar calendar(List<SchoolEvent> events, Map<Integer, Integer> participantsByEvent, LocalDate today) {
        List<EventCalendarEntry> entries = events.stream()
                .map(event -> new EventCalendarEntry(event, participantsByEvent.getOrDefault(event.getId(), 0), statusOf(event, today)))
                .sorted(Comparator.comparingInt((EventCalendarEntry e) -> group(e.status()))
                        .thenComparing((a, b) -> group(a.status()) == 1
                                ? b.event().getDate().compareTo(a.event().getDate())
                                : a.event().getDate().compareTo(b.event().getDate()))
                        .thenComparing(e -> e.event().getName(), SPANISH))
                .toList();
        return new EventCalendar(entries, summarize(entries));
    }

    private static int group(EventStatus status) {
        return switch (status) {
            case TODAY, UPCOMING -> 0;
            case HELD -> 1;
            case CANCELLED -> 2;
        };
    }

    public static EventSummary summarize(List<EventCalendarEntry> entries) {
        Map<EventType, Long> byType = entries.stream()
                .filter(e -> e.status() != EventStatus.CANCELLED)
                .collect(Collectors.groupingBy(e -> e.event().getType(), () -> new EnumMap<>(EventType.class), Collectors.counting()));
        return new EventSummary(
                entries.size(),
                (int) entries.stream().filter(e -> e.status() == EventStatus.UPCOMING || e.status() == EventStatus.TODAY).count(),
                (int) entries.stream().filter(e -> e.status() == EventStatus.HELD).count(),
                (int) entries.stream().filter(e -> e.status() == EventStatus.CANCELLED).count(),
                entries.stream().mapToInt(EventCalendarEntry::participants).sum(),
                byType);
    }

    /** 1er, 2do, 3er Lugar, Participante, then no position; students with the same position by last name. */
    public static EventResults results(SchoolEvent event, List<EventParticipation> participations) {
        Comparator<EventParticipation> byPosition = Comparator.comparingInt(
                p -> p.getPosition() == null ? Integer.MAX_VALUE : p.getPosition().ordinal());
        List<EventParticipation> sorted = participations.stream()
                .sorted(byPosition
                        .thenComparing(p -> p.getStudent().getLastName(), SPANISH)
                        .thenComparing(p -> p.getStudent().getFirstName(), SPANISH))
                .toList();
        Map<EventPosition, Long> counts = sorted.stream()
                .filter(p -> p.getPosition() != null)
                .collect(Collectors.groupingBy(EventParticipation::getPosition, () -> new EnumMap<>(EventPosition.class), Collectors.counting()));
        return new EventResults(event, sorted, counts);
    }

    public static StudentEventHistory history(List<EventParticipation> participations) {
        List<EventParticipation> newestFirst = participations.stream()
                .sorted(Comparator.comparing((EventParticipation p) -> p.getEvent().getDate()).reversed())
                .toList();
        long podiums = newestFirst.stream()
                .filter(p -> p.getPosition() != null && p.getPosition().isPodium())
                .count();
        return new StudentEventHistory(newestFirst, podiums);
    }
}
