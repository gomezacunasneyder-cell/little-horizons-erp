package com.littlehorizons.erp.controller.event;

import com.littlehorizons.erp.model.entity.SchoolEvent;

/** An event with its number of participants and its status relative to today. */
public record EventCalendarEntry(SchoolEvent event, int participants, EventStatus status) {
}
