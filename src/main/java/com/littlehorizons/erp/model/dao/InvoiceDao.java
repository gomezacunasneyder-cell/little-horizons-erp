package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Invoice;
import com.littlehorizons.erp.model.entity.Student;

import java.util.List;
import java.util.Optional;

public interface InvoiceDao {

    /**
     * Completes the invoice with the data that depends on its number (CUFE, resolution). The DAO calls it
     * inside the transaction, right after the invoice already has its consecutive number. If it throws a
     * ValidationException (for example, the number is outside the authorized range) nothing is saved.
     */
    @FunctionalInterface
    interface Seal {
        void apply(Invoice invoice);
    }

    /**
     * Saves the invoice in ONE transaction: takes the next invoice number, refuses (ValidationException) a payment
     * that is already in another invoice, lets the seal add the CUFE and the resolution, and inserts the invoice and
     * its lines. The invoice receives its id and its number only if everything was saved. The check of "already
     * invoiced" happens after the lock on the invoice counter, so two people invoicing the same payment at the
     * same time cannot both succeed (the UNIQUE key of detalle_factura is the last barrier).
     */
    void create(Invoice invoice, Seal seal) throws DaoException;

    /** The invoice with that number (FE-000123), with its client, student and lines. */
    Optional<Invoice> findByNumber(String number) throws DaoException;

    /** The invoices of a student, newest first. */
    List<Invoice> findByStudent(Student student) throws DaoException;
}
