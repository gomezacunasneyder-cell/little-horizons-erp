package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Loan;
import com.littlehorizons.erp.model.enums.BorrowerType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Loans of students and of teachers behind ONE interface: each method chooses the right table
 * (prestamos_estudiante or prestamos_profesor) from the borrower type, and the lists that mix
 * both kinds are built with a UNION, so the controller never knows there are two tables.
 */
public interface LoanDao {

    /**
     * Registers the loan and marks its resource "Prestado" in ONE transaction. The resource is
     * changed with a guarded UPDATE (only if it is still "Disponible"), so two people cannot get
     * the same item. Returns false, without saving anything, when the resource was not available.
     * On success the loan receives its id.
     */
    boolean lend(Loan loan) throws DaoException;

    /**
     * Saves the return of a loan that already carries its return date, state and observations,
     * and sets its resource back to "Disponible", in ONE transaction. Both changes are guarded
     * (the loan must still be open, the resource still "Prestado"). Returns false, without
     * saving anything, when the loan was not open any more.
     */
    boolean giveBack(Loan loan) throws DaoException;

    /** The open loan (Activo or Vencido) of a resource, if it is out. */
    Optional<Loan> findOpenByResource(int resourceId) throws DaoException;

    /** Every open loan (Activo and Vencido) of students and teachers, the earliest due date first. */
    List<Loan> findOpen() throws DaoException;

    /**
     * The open loans whose scheduled date is BEFORE {@code today} (the day it is due is not late yet),
     * the earliest due date first. It decides by the date, not by the stored state, so the answer is
     * right even if markOverdue has not run.
     */
    List<Loan> findOverdue(LocalDate today) throws DaoException;

    /** Every loan of a resource, the most recent first. */
    List<Loan> findByResource(int resourceId) throws DaoException;

    /** Every loan of one student or one teacher, the most recent first. */
    List<Loan> findByBorrower(BorrowerType type, int borrowerId) throws DaoException;

    /**
     * Changes "Activo" to "Vencido" in the loans whose scheduled date is BEFORE {@code today}.
     * Returns how many loans changed. Nothing runs by itself in the database, so the controller
     * calls this before it reads loans.
     */
    int markOverdue(LocalDate today) throws DaoException;
}
