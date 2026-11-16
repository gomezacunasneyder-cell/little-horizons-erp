package com.littlehorizons.erp.controller.event;

import com.littlehorizons.erp.model.enums.EventType;

import java.util.Map;

/** Totals of the calendar. byType counts the events that were not cancelled, per type. */
public record EventSummary(int total, int upcoming, int held, int cancelled, int participations,
                           Map<EventType, Long> byType) {
}
