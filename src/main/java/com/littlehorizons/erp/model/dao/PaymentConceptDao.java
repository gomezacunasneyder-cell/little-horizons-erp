package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.PaymentConcept;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentConceptDao {

    void create(PaymentConcept concept) throws DaoException;

    /** Updates every editable field, including the active flag. */
    void update(PaymentConcept concept) throws DaoException;

    Optional<PaymentConcept> findById(int id) throws DaoException;

    /** Every concept, active or not (the controller decides the display order). */
    List<PaymentConcept> findAll() throws DaoException;

    /** Case-insensitive search in the code, the name and the description. */
    List<PaymentConcept> search(String term) throws DaoException;

    /** True when another concept has this code (excludeId = the concept being edited, or null). */
    boolean codeExists(String code, Integer excludeId) throws DaoException;

    /** True when another concept has this name, ignoring case and accents. */
    boolean nameExists(String name, Integer excludeId) throws DaoException;

    /** Concept id -> number of students with a personalized value, for the concepts that have any. */
    Map<Integer, Integer> countPersonalizedByConcept() throws DaoException;
}
