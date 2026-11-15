package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Club;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ClubDao {

    void create(Club club) throws DaoException;

    /**
     * Updates every editable field. It refuses (DaoException) a capacity lower than the number of
     * active members, checked inside a transaction that locks the club so it cannot race with an enrollment.
     */
    void update(Club club) throws DaoException;

    Optional<Club> findById(int id) throws DaoException;

    /** All the clubs (active and closed) ordered by name. */
    List<Club> findAll() throws DaoException;

    /** Case-insensitive search in the name, the description and the teacher in charge. */
    List<Club> search(String term) throws DaoException;

    /** True when another club already has that name (excludeId = the club being edited, or null). */
    boolean nameExists(String name, Integer excludeId) throws DaoException;

    /** Number of ACTIVE members of one club. */
    int countActiveMembers(int clubId) throws DaoException;

    /** Club id -> number of ACTIVE members, for every club with at least one. */
    Map<Integer, Integer> countActiveMembersByClub() throws DaoException;
}
