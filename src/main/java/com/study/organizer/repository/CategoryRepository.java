package com.study.organizer.repository;

import java.util.List;

/**
 * Remembers the study categories the user has used.
 *
 * <p>Categories are created on the fly: the user types a name when starting a
 * session, and if it has not been seen before it is remembered so it appears in
 * the drop-down next time. There is no separate screen for managing them.
 *
 * <p>Like {@link SessionRepository} this is an interface, so the rest of the
 * application never depends on the file layout directly and the views can be tested
 * against a simple in-memory stand-in.
 */
public interface CategoryRepository {

    /**
     * Remembers a category name, creating it if this is the first time it is used.
     *
     * <p>Implementations must be safe to call repeatedly with the same name —
     * the application calls this on every save rather than checking first.
     *
     * @param name the category name; blank names are ignored
     * @throws RepositoryException if the write fails
     */
    void ensureExists(String name);

    /**
     * Loads every category the user has used, in alphabetical order.
     *
     * @return the category names; never {@code null}
     * @throws RepositoryException if the read fails
     */
    List<String> findAll();
}
