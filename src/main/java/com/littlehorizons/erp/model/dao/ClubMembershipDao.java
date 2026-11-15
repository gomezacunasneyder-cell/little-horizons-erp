package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.ClubMembership;

import java.util.List;

public interface ClubMembershipDao {

    /**
     * Saves the membership ONLY if the club still has room. Inside one transaction it locks the club
     * row, counts the active members against the capacity stored in the database and inserts. The
     * lock makes two simultaneous enrollments take turns, so the capacity can never be exceeded.
     *
     * @return true when saved; false when the club was already full
     */
    boolean createIfRoom(ClubMembership membership) throws DaoException;

    /** Saves the withdrawal (state, date and reason) of an active membership; false if it was not active. */
    boolean saveWithdrawal(ClubMembership membership) throws DaoException;

    boolean existsActive(int studentId, int clubId) throws DaoException;

    /** Every membership of a club, active ones first. */
    List<ClubMembership> findByClub(int clubId) throws DaoException;

    /** Every membership of a student (all the clubs, active and withdrawn), newest first. */
    List<ClubMembership> findByStudent(int studentId) throws DaoException;
}
