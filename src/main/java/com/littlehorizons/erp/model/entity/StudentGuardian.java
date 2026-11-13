package com.littlehorizons.erp.model.entity;

import java.util.Objects;

/**
 * The link between one student and one guardian (table estudiante_acudiente). It is its own
 * class, and not just a field of Guardian, because the same guardian can be the main contact
 * of one child and a secondary contact of another: the flags belong to the relationship.
 */
public class StudentGuardian {

    private final Guardian guardian;
    private boolean principal;
    private boolean emergencyContact;

    public StudentGuardian(Guardian guardian, boolean principal, boolean emergencyContact) {
        this.guardian = Objects.requireNonNull(guardian, "guardian");
        this.principal = principal;
        this.emergencyContact = emergencyContact;
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public void setPrincipal(boolean principal) {
        this.principal = principal;
    }

    public boolean isEmergencyContact() {
        return emergencyContact;
    }

    public void setEmergencyContact(boolean emergencyContact) {
        this.emergencyContact = emergencyContact;
    }
}
