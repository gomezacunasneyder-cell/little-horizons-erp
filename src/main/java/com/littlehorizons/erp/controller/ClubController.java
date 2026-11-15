package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.controller.club.ClubAnalyzer;
import com.littlehorizons.erp.controller.club.ClubCatalog;
import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.dao.ClubDao;
import com.littlehorizons.erp.model.entity.Club;
import com.littlehorizons.erp.model.entity.Teacher;
import com.littlehorizons.erp.util.ErrorLogger;

import java.util.List;

/**
 * Rules of the club catalog: a unique name, a teacher in charge who is ACTIVE, a capacity that is
 * never lower than the members already inside, and a club can only be closed when nobody is
 * active in it (so a closed club never keeps active members).
 */
public class ClubController extends BaseController {

    private final ClubDao clubDao;

    public ClubController(ClubDao clubDao, ErrorLogger logger) {
        super(logger);
        this.clubDao = clubDao;
    }

    public Result<Club> create(Club club) {
        return execute("Create club", () -> {
            requireActiveSupervisor(club.getSupervisor());
            requireFreeName(club);
            clubDao.create(club);
            return Result.ok(club, "Club «" + club.getName() + "» creado.");
        });
    }

    /** Saves the edited data of an existing club (the Club carries its id and its current active flag). */
    public Result<Club> update(Club club) {
        return execute("Update club", () -> {
            requireActiveSupervisor(club.getSupervisor());
            requireFreeName(club);
            int members = clubDao.countActiveMembers(club.getId());
            if (club.getMaxCapacity() < members) {
                throw new ValidationException("El cupo no puede ser menor que los " + members
                        + " miembros activos del club.");
            }
            clubDao.update(club);
            return Result.ok(club, "Club «" + club.getName() + "» actualizado.");
        });
    }

    /** Closes or reopens a club. Closing needs zero active members: withdraw them first. */
    public Result<Club> setActive(Club club, boolean active) {
        return execute(active ? "Reopen club" : "Close club", () -> {
            if (club.isActive() == active) {
                throw new ValidationException("El club ya está " + (active ? "activo." : "cerrado."));
            }
            if (active) {
                requireActiveSupervisor(club.getSupervisor());
            } else {
                int members = clubDao.countActiveMembers(club.getId());
                if (members > 0) {
                    throw new ValidationException("No se puede cerrar: el club tiene " + members
                            + " miembro(s) activo(s). Retírelos primero.");
                }
            }
            club.setActive(active);
            try {
                clubDao.update(club);
            } catch (RuntimeException | DaoException e) {
                club.setActive(!active); // the database kept the old value, so the object must too
                throw e;
            }
            return Result.ok(club, "Club «" + club.getName() + "» " + (active ? "reabierto." : "cerrado."));
        });
    }

    public Result<ClubCatalog> catalog() {
        return execute("Show club catalog", () ->
                Result.ok(ClubAnalyzer.catalog(clubDao.findAll(), clubDao.countActiveMembersByClub()), ""));
    }

    /** True when another club already uses that name (excludeId = the club being edited, or null). */
    public Result<Boolean> nameTaken(String name, Integer excludeId) {
        return execute("Check club name", () -> Result.ok(clubDao.nameExists(name.trim(), excludeId), ""));
    }

    public Result<List<Club>> search(String term) {
        return execute("Search clubs", () -> Result.ok(clubDao.search(requireTerm(term)), ""));
    }

    /** Same search but only clubs that accept students. */
    public Result<List<Club>> searchActive(String term) {
        return execute("Search active clubs", () -> Result.ok(
                clubDao.search(requireTerm(term)).stream().filter(Club::isActive).toList(), ""));
    }

    private static String requireTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new ValidationException("Escriba parte del nombre del club, de su descripción o del profesor.");
        }
        return term.trim();
    }

    private void requireFreeName(Club club) throws DaoException {
        if (clubDao.nameExists(club.getName(), club.getId())) {
            throw new ValidationException("Ya existe un club con el nombre «" + club.getName() + "».");
        }
    }

    private static void requireActiveSupervisor(Teacher teacher) {
        if (!teacher.isActive()) {
            throw new ValidationException("El profesor " + teacher.getFullName() + " está "
                    + teacher.getStatus().getDisplayName().toLowerCase() + ". Elija un profesor activo.");
        }
    }
}
