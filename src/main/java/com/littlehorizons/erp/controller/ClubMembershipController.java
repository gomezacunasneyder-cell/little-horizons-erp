package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.ClubDao;
import com.littlehorizons.erp.model.dao.ClubMembershipDao;
import com.littlehorizons.erp.model.entity.Club;
import com.littlehorizons.erp.model.entity.ClubMembership;
import com.littlehorizons.erp.model.entity.Student;
import com.littlehorizons.erp.model.enums.StudentStatus;
import com.littlehorizons.erp.util.ErrorLogger;

import java.time.LocalDate;
import java.util.List;

/**
 * Rules for joining and leaving clubs: only ACTIVE students, only open clubs, never twice in the
 * same club at the same time, and never beyond the capacity. The controller checks the rules to
 * give a clear message; the DAO repeats the capacity check inside a locked transaction, so two
 * enrollments at the same moment cannot exceed it either.
 */
public class ClubMembershipController extends BaseController {

    private final ClubMembershipDao membershipDao;
    private final ClubDao clubDao;

    public ClubMembershipController(ClubMembershipDao membershipDao, ClubDao clubDao, ErrorLogger logger) {
        super(logger);
        this.membershipDao = membershipDao;
        this.clubDao = clubDao;
    }

    public Result<ClubMembership> enroll(Student student, Club club, LocalDate joinDate) {
        return execute("Enroll student in club", () -> {
            ClubMembership membership = new ClubMembership(student, club, joinDate); // validates the date
            if (student.getStatus() != StudentStatus.ACTIVE) {
                throw new ValidationException("Solo los estudiantes activos pueden inscribirse en un club ("
                        + student.getFullName() + " está " + student.getStatus().getDisplayName().toLowerCase() + ").");
            }
            // read the club again: its capacity or state may have changed since the user picked it
            Club current = clubDao.findById(club.getId()).orElse(null);
            if (current == null) {
                throw new ValidationException("El club ya no existe.");
            }
            if (!current.isActive()) {
                throw new ValidationException("El club «" + current.getName() + "» está cerrado.");
            }
            if (membershipDao.existsActive(student.getId(), current.getId())) {
                throw new ValidationException(student.getFullName() + " ya es miembro activo de «" + current.getName() + "».");
            }
            int members = clubDao.countActiveMembers(current.getId());
            if (members >= current.getMaxCapacity()) {
                throw new ValidationException("El club «" + current.getName() + "» está lleno (cupo "
                        + current.getMaxCapacity() + ").");
            }
            if (!membershipDao.createIfRoom(membership)) {
                throw new ValidationException("El club «" + current.getName() + "» se llenó justo ahora. No se inscribió.");
            }
            return Result.ok(membership, student.getFullName() + " inscrito en «" + current.getName()
                    + "» (" + (members + 1) + " de " + current.getMaxCapacity() + " cupos).");
        });
    }

    public Result<ClubMembership> withdraw(ClubMembership membership, LocalDate date, String reason) {
        return execute("Withdraw student from club", () -> {
            membership.withdraw(date, reason); // validates state and dates
            if (!membershipDao.saveWithdrawal(membership)) {
                throw new ValidationException("La membresía ya estaba retirada.");
            }
            return Result.ok(membership, membership.getStudent().getFullName() + " retirado de «"
                    + membership.getClub().getName() + "».");
        });
    }

    /** Members of a club: the active ones first, then the ones who left. */
    public Result<List<ClubMembership>> byClub(Club club) {
        return execute("List club members", () -> Result.ok(membershipDao.findByClub(club.getId()), ""));
    }

    /** Every club the student has belonged to, newest first. */
    public Result<List<ClubMembership>> byStudent(Student student) {
        return execute("List student clubs", () -> Result.ok(membershipDao.findByStudent(student.getId()), ""));
    }

    /** Only the memberships that are still active (the ones that can be withdrawn). */
    public Result<List<ClubMembership>> activeByStudent(Student student) {
        return execute("List active clubs of student", () -> Result.ok(
                membershipDao.findByStudent(student.getId()).stream().filter(ClubMembership::isActive).toList(), ""));
    }
}
