package com.littlehorizons.erp.controller.event;

import com.littlehorizons.erp.model.entity.EventParticipation;
import com.littlehorizons.erp.model.entity.SchoolEvent;
import com.littlehorizons.erp.model.enums.EventPosition;

import java.util.List;
import java.util.Map;

/** The participants of an event sorted by position, and how many got each position. */
public record EventResults(SchoolEvent event, List<EventParticipation> participants,
                           Map<EventPosition, Long> positionCounts) {
}
