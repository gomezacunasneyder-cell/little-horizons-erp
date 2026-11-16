package com.littlehorizons.erp.controller.event;

import com.littlehorizons.erp.model.entity.EventParticipation;

import java.util.List;

/** Every event a student took part in (newest first) and how many times he or she reached the podium. */
public record StudentEventHistory(List<EventParticipation> participations, long podiums) {
}
