package com.study.organizer.repository;

import com.study.organizer.model.StudySession;

import java.util.List;

/**
 * Reads and writes study sessions.
 *
 * <p>This is an <b>interface</b> rather than a concrete class on purpose. The
 * dashboard and the statistics service depend only on this contract, so they
 * never touch a file path directly. Two things follow from that:
 *
 * <ul>
 *   <li>Storage can be swapped — for a local file, or an in-memory fake used in
 *       tests — without changing any code that displays data.</li>
 *   <li>The boundary between "our application" and "the database" is visible in
 *       one small file, which makes the design easy to follow.</li>
 * </ul>
 */
public interface SessionRepository {

    /**
     * Saves a finished session.
     *
     * @param session the session to store; its id is expected to be {@code null}
     * @return the same session with the id assigned by the database
     * @throws RepositoryException if the write fails
     */
    StudySession save(StudySession session);

    /**
     * Overwrites an existing session with corrected details.
     *
     * <p>Used when the user edits a past session — to fix a category, correct a
     * duration that was left running too long, or add a summary they rushed.
     *
     * @param session the session to overwrite; its id must not be {@code null}
     * @return the same session
     * @throws IllegalArgumentException if the session has no id
     * @throws RepositoryException      if the write fails
     */
    StudySession update(StudySession session);

    /**
     * Permanently removes a session.
     *
     * @param id the document id of the session to remove
     * @throws RepositoryException if the delete fails
     */
    void delete(String id);

    /**
     * Loads every saved session, newest first.
     *
     * <p>The whole history is loaded at once. For a personal study log this is
     * a few hundred small documents at most, so the simplicity is worth far more
     * than the paging machinery that would be needed to avoid it.
     *
     * @return all sessions, ordered by start time descending; never {@code null}
     * @throws RepositoryException if the read fails
     */
    List<StudySession> findAll();
}
