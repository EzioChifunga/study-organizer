package com.study.organizer.repository;

/**
 * Signals that a database read or write failed.
 *
 * <p>File work throws {@link java.io.IOException} for a dozen unrelated reasons,
 * and it is checked. Wrapping those in this one unchecked type means the UI can
 * catch a single thing and show one clear message, and that the
 * {@link SessionRepository} interface does not have to mention a storage-specific
 * exception in its signatures.
 *
 * <p>The original cause is always kept, so nothing is lost when debugging.
 */
public class RepositoryException extends RuntimeException {

    /**
     * @param message a plain-language description of what could not be done
     * @param cause   the underlying failure from the database driver
     */
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message a plain-language description of what could not be done
     */
    public RepositoryException(String message) {
        super(message);
    }
}
