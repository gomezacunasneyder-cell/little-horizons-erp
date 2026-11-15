package com.littlehorizons.erp.controller.club;

import com.littlehorizons.erp.model.entity.Club;

/** A club together with how many ACTIVE members it has right now (a record: immutable data). */
public record ClubOccupancy(Club club, int activeMembers) {

    public int availableSpots() {
        return Math.max(0, club.getMaxCapacity() - activeMembers);
    }

    public boolean isFull() {
        return activeMembers >= club.getMaxCapacity();
    }
}
