package com.littlehorizons.erp.controller.event;

import java.util.List;

/** The calendar ready for the view: events in display order plus the totals. */
public record EventCalendar(List<EventCalendarEntry> entries, EventSummary summary) {
}
