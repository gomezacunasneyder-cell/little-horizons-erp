package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.MembershipStatus;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The participation of one student in one club: the date the student joined and its state
 * (Activo or Retirado). A student can belong to several clubs, and can come back to a club
 * after leaving it; each stay is a separate membership, so the history is never lost.
 */
public class ClubMembership {

    public static final int REASON_MAX = 300;

    private Integer id;
    private final Student student;
    private final Club club;
    private final LocalDate joinDate;
    private MembershipStatus status = MembershipStatus.ACTIVE;
    private LocalDate withdrawalDate;
    private String withdrawalReason;

    public ClubMembership(Student student, Club club, LocalDate joinDate) {
        this.student = Objects.requireNonNull(student, "student");
        this.club = Objects.requireNonNull(club, "club");
        this.joinDate = checkJoinDate(joinDate);
    }

    private static LocalDate checkJoinDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("La fecha de ingreso es obligatoria.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de ingreso no puede estar en el futuro.");
        }
        if (date.getYear() < Enrollment.MIN_YEAR) {
            throw new ValidationException("El año debe ser " + Enrollment.MIN_YEAR + " o posterior.");
        }
        return date;
    }

    /**
     * Closes the membership. The date cannot be in the future or before the joining date, and the
     * reason is optional. Withdrawing twice is an error: the first withdrawal is the real one.
     */
    public void withdraw(LocalDate date, String reason) {
        if (status == MembershipStatus.WITHDRAWN) {
            throw new ValidationException("La membresía ya está retirada.");
        }
        if (date == null) {
            throw new ValidationException("La fecha de retiro es obligatoria.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de retiro no puede estar en el futuro.");
        }
        if (date.isBefore(joinDate)) {
            throw new ValidationException("La fecha de retiro no puede ser anterior a la de ingreso (" + joinDate + ").");
        }
        String clean = reason == null ? "" : reason.trim();
        if (clean.length() > REASON_MAX) {
            throw new ValidationException("El motivo admite máximo " + REASON_MAX + " caracteres.");
        }
        this.status = MembershipStatus.WITHDRAWN;
        this.withdrawalDate = date;
        this.withdrawalReason = clean.isEmpty() ? null : clean;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public Club getClub() {
        return club;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public LocalDate getWithdrawalDate() {
        return withdrawalDate;
    }

    public String getWithdrawalReason() {
        return withdrawalReason;
    }
}
