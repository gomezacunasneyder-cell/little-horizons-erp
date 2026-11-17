package com.littlehorizons.erp.model.dao;

import com.littlehorizons.erp.exception.DaoException;
import com.littlehorizons.erp.model.entity.Resource;
import com.littlehorizons.erp.model.enums.ResourceStatus;

import java.util.List;
import java.util.Optional;

public interface ResourceDao {

    /** Inserts the resource (with the state it carries) and fills its id. */
    void create(Resource resource) throws DaoException;

    /** Updates the data of the resource but NOT its state: the state only changes through changeStatus. */
    void update(Resource resource) throws DaoException;

    Optional<Resource> findById(int id) throws DaoException;

    /** Every resource, sorted by code. */
    List<Resource> findAll() throws DaoException;

    /** Case-insensitive search in the code, the name, the description, the location and the category. */
    List<Resource> search(String term) throws DaoException;

    /** True when another resource has this code (excludeId = the resource being edited, or null). */
    boolean codeExists(String code, Integer excludeId) throws DaoException;

    /**
     * Moves the resource from one state to another only if it is STILL in the expected state
     * (a single guarded UPDATE, so a loan registered in the meantime is never overwritten).
     * Returns false when nothing changed.
     */
    boolean changeStatus(int id, ResourceStatus from, ResourceStatus to) throws DaoException;

    /**
     * Deletes the resource only if it has no loan history and is not borrowed, in ONE statement:
     * the loan tables delete their rows with the resource (ON DELETE CASCADE), so the check and
     * the delete cannot be separated. Returns false when nothing was deleted.
     */
    boolean deleteIfNeverBorrowed(int id) throws DaoException;
}
