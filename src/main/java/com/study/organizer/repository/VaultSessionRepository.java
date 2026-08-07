package com.study.organizer.repository;

import com.study.organizer.model.StudySession;
import com.study.organizer.vault.MalformedNoteException;
import com.study.organizer.vault.ObsidianVault;
import com.study.organizer.vault.SessionNote;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stores study sessions as Markdown notes in an Obsidian vault.
 *
 * <p>Each session is one note in its category's folder, and the note's path
 * relative to the vault root is the session's id. There is no index file and no
 * database: the folder tree <i>is</i> the data, so anything you do in Obsidian —
 * renaming a note, editing a summary, moving a file between category folders —
 * is picked up the next time the application reads the vault.
 *
 * <p>The cost of that choice is that reading everything means opening every
 * file. For a personal study log that is a few hundred small files and takes
 * milliseconds, which is a good trade for never having two sources of truth.
 *
 * <p>Files that are not session notes are skipped rather than reported as
 * errors. A vault is full of ordinary notes, and only the ones carrying session
 * frontmatter are records.
 */
public class VaultSessionRepository implements SessionRepository {

    private final ObsidianVault vault;
    private final ZoneId zone;

    /**
     * @param vault the vault to store sessions in
     */
    public VaultSessionRepository(ObsidianVault vault) {
        this(vault, ZoneId.systemDefault());
    }

    /**
     * @param vault the vault to store sessions in
     * @param zone  the time zone timestamps are written and read in
     */
    public VaultSessionRepository(ObsidianVault vault, ZoneId zone) {
        this.vault = vault;
        this.zone = zone;
    }

    @Override
    public StudySession save(StudySession session) {
        String path = vault.reserveNotePath(
                session.getCategory(), SessionNote.fileNameFor(session, zone));

        StudySession saved = session.withId(path);
        vault.writeNote(path, SessionNote.toMarkdown(saved, zone));
        return saved;
    }

    @Override
    public StudySession update(StudySession session) {
        if (session.getId() == null) {
            throw new IllegalArgumentException(
                    "Cannot update a session that has never been saved - it has no note.");
        }

        String path = session.getId();

        // If the category changed, the note has to move: the folder is the
        // category, so leaving the file where it was would make the note
        // disagree with its own frontmatter.
        String currentCategory = ObsidianVault.categoryOf(path);
        if (!currentCategory.equalsIgnoreCase(session.getCategory())) {
            path = vault.moveNote(path, session.getCategory());
        }

        StudySession moved = session.withId(path);
        vault.writeNote(path, SessionNote.toMarkdown(moved, zone));
        return moved;
    }

    @Override
    public void delete(String id) {
        vault.deleteNote(id);
    }

    @Override
    public List<StudySession> findAll() {
        List<StudySession> sessions = new ArrayList<>();

        for (String path : vault.listAllNotePaths()) {
            try {
                sessions.add(SessionNote.fromMarkdown(path, vault.readNote(path), zone));
            } catch (MalformedNoteException e) {
                // Not a session note - one of the user's own notes. Skipping is
                // the correct behaviour, not an error to report.
            }
        }

        sessions.sort(Comparator.comparing(StudySession::getStartedAt).reversed());
        return sessions;
    }

    /** @return the vault this repository reads and writes */
    public ObsidianVault getVault() {
        return vault;
    }
}
