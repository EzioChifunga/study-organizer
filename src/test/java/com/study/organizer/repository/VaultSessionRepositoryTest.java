package com.study.organizer.repository;

import com.study.organizer.model.StudySession;
import com.study.organizer.service.PointsCalculator;
import com.study.organizer.vault.ObsidianVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the storage contract against a real vault in a temporary folder.
 *
 * <p>{@code @TempDir} gives each test its own empty folder and deletes it
 * afterwards, so these exercise genuine file reading and writing rather than a
 * stand-in. That matters here: the whole point of this design is that the folder
 * tree <i>is</i> the data, so a test that avoided the file system would be
 * testing nothing that ships.
 */
class VaultSessionRepositoryTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    @TempDir
    Path vaultFolder;

    private ObsidianVault vault;
    private VaultSessionRepository sessions;
    private VaultCategoryRepository categories;

    @BeforeEach
    void setUp() {
        vault = new ObsidianVault(vaultFolder);
        sessions = new VaultSessionRepository(vault, ZONE);
        categories = new VaultCategoryRepository(vault);
    }

    private static StudySession session(String category, int daysAgo, long seconds,
                                        String summary, List<String> references) {
        Instant startedAt = LocalDateTime.of(2026, 8, 7, 13, 46)
                .atZone(ZONE).toInstant()
                .minus(daysAgo, ChronoUnit.DAYS);

        return new StudySession(
                null, category, startedAt, startedAt.plusSeconds(seconds), seconds,
                PointsCalculator.fromSeconds(seconds), summary, "", references);
    }

    // ------------------------------------------------------------ categories

    @Test
    @DisplayName("creating a category creates a folder")
    void creatingACategoryCreatesAFolder() {
        categories.ensureExists("Java");

        assertTrue(Files.isDirectory(vaultFolder.resolve("Java")),
                "A category must exist on disk as a folder.");
        assertEquals(List.of("Java"), categories.findAll());
    }

    @Test
    @DisplayName("a folder added by hand appears as a category")
    void foldersMadeInObsidianBecomeCategories() throws IOException {
        // Exactly what happens when the user makes a folder in Obsidian.
        Files.createDirectory(vaultFolder.resolve("Philosophy"));

        assertTrue(categories.findAll().contains("Philosophy"),
                "The folders and the category list must be the same thing.");
    }

    @Test
    @DisplayName("Obsidian's own folders are not offered as categories")
    void hidesReservedFolders() throws IOException {
        Files.createDirectory(vaultFolder.resolve(".obsidian"));
        Files.createDirectory(vaultFolder.resolve("Java"));

        assertEquals(List.of("Java"), categories.findAll());
    }

    // -------------------------------------------------------------- sessions

    @Test
    @DisplayName("saving writes a note inside the category folder")
    void savingWritesANoteInTheCategoryFolder() {
        StudySession saved = sessions.save(
                session("Java", 0, 3300, "Worked through interfaces.", List.of()));

        assertNotNull(saved.getId());
        assertTrue(saved.getId().startsWith("Java/"),
                "A session's note must live in its category's folder, but was " + saved.getId());
        assertTrue(Files.isRegularFile(vaultFolder.resolve(saved.getId())));
    }

    @Test
    @DisplayName("two sessions in the same minute do not overwrite each other")
    void avoidsFileNameCollisions() {
        StudySession first = sessions.save(session("Java", 0, 3300, "Same summary", List.of()));
        StudySession second = sessions.save(session("Java", 0, 1800, "Same summary", List.of()));

        assertFalse(first.getId().equals(second.getId()),
                "Each session needs its own file.");
        assertEquals(2, sessions.findAll().size());
    }

    @Test
    @DisplayName("everything written comes back, newest first")
    void readsBackNewestFirst() {
        sessions.save(session("Java", 5, 3600, "Oldest", List.of()));
        sessions.save(session("Java", 0, 3600, "Newest", List.of()));
        sessions.save(session("Algorithms", 2, 3600, "Middle", List.of()));

        List<StudySession> all = sessions.findAll();

        assertEquals(3, all.size());
        assertEquals("Newest", all.get(0).getSummary());
        assertEquals("Middle", all.get(1).getSummary());
        assertEquals("Oldest", all.get(2).getSummary());
    }

    @Test
    @DisplayName("ordinary notes in the vault are skipped, not treated as sessions")
    void ignoresNonSessionNotes() {
        sessions.save(session("Java", 0, 3600, "A real session", List.of()));

        // The kind of note a person actually keeps a vault for.
        vault.writeNote("Java/Notes on generics.md",
                "# Notes on generics\n\nType erasure means...\n");

        List<StudySession> all = sessions.findAll();

        assertEquals(1, all.size(), "Only session notes are records.");
        assertEquals("A real session", all.get(0).getSummary());
    }

    @Test
    @DisplayName("editing overwrites the same note rather than adding one")
    void updatingOverwritesInPlace() {
        StudySession saved = sessions.save(session("Java", 0, 3600, "First try", List.of()));

        sessions.update(new StudySession(
                saved.getId(), "Java", saved.getStartedAt(), saved.getEndedAt(),
                1800, PointsCalculator.fromSeconds(1800), "Corrected", "A note", List.of()));

        List<StudySession> all = sessions.findAll();
        assertEquals(1, all.size(), "Editing must not create a second record.");
        assertEquals("Corrected", all.get(0).getSummary());
        assertEquals("A note", all.get(0).getObservations());
        assertEquals(1800, all.get(0).getDurationSeconds());
    }

    @Test
    @DisplayName("changing the category moves the note to the other folder")
    void changingCategoryMovesTheNote() {
        StudySession saved = sessions.save(session("Java", 0, 3600, "Studied", List.of()));
        String originalPath = saved.getId();

        StudySession moved = sessions.update(new StudySession(
                originalPath, "Algorithms", saved.getStartedAt(), saved.getEndedAt(),
                saved.getDurationSeconds(), saved.getPoints(), "Studied", "", List.of()));

        assertTrue(moved.getId().startsWith("Algorithms/"),
                "The note must follow its category, but stayed at " + moved.getId());
        assertFalse(Files.exists(vaultFolder.resolve(originalPath)),
                "The note must not be left behind in the old folder.");
        assertEquals(1, sessions.findAll().size());
    }

    @Test
    @DisplayName("updating a session that was never saved is refused")
    void updatingWithoutAnIdIsRefused() {
        StudySession unsaved = session("Java", 0, 3600, "Studied", List.of());
        assertThrows(IllegalArgumentException.class, () -> sessions.update(unsaved));
    }

    @Test
    @DisplayName("deleting removes the file")
    void deletingRemovesTheFile() {
        StudySession saved = sessions.save(session("Java", 0, 3600, "Studied", List.of()));

        sessions.delete(saved.getId());

        assertFalse(Files.exists(vaultFolder.resolve(saved.getId())));
        assertTrue(sessions.findAll().isEmpty());
    }

    // ------------------------------------------------------------ references

    @Test
    @DisplayName("the picker is only ever offered notes from one folder")
    void listsOnlyTheCategoryFolder() {
        vault.writeNote("Java/Notes on generics.md", "# Notes on generics\n");
        vault.writeNote("Java/Streams API.md", "# Streams API\n");
        vault.writeNote("Algorithms/Big O reference.md", "# Big O reference\n");

        List<String> javaNotes = vault.listNoteTitles("Java");

        assertTrue(javaNotes.contains("Notes on generics"));
        assertTrue(javaNotes.contains("Streams API"));
        assertFalse(javaNotes.contains("Big O reference"),
                "A category must not be offered another category's notes.");
    }

    @Test
    @DisplayName("references survive a round trip through the file")
    void referencesRoundTrip() {
        vault.writeNote("Java/Notes on generics.md", "# Notes on generics\n");

        sessions.save(session("Java", 0, 3600, "Studied", List.of("Notes on generics")));

        assertEquals(List.of("Notes on generics"),
                sessions.findAll().get(0).getReferences());
    }

    @Test
    @DisplayName("a link resolves within its folder, and only there")
    void resolvesLinksWithinTheFolder() {
        vault.writeNote("Java/Notes on generics.md", "# Notes on generics\n");
        vault.writeNote("Algorithms/Big O reference.md", "# Big O reference\n");

        assertEquals("Java/Notes on generics.md",
                vault.resolveLink("Java", "Notes on generics"));

        // Obsidian matches link titles case-insensitively, so this must too.
        assertEquals("Java/Notes on generics.md",
                vault.resolveLink("Java", "notes on generics"));

        assertNull(vault.resolveLink("Java", "Big O reference"),
                "A link must not reach into another category's folder.");
    }

    @Test
    @DisplayName("a link to a note that no longer exists resolves to nothing")
    void reportsBrokenLinks() {
        assertNull(vault.resolveLink("Java", "Renamed in Obsidian"));
    }

    @Test
    @DisplayName("paths that try to escape the vault are refused")
    void refusesPathsOutsideTheVault() {
        assertThrows(RepositoryException.class,
                () -> vault.readNote("../../etc/passwd"));
        assertThrows(RepositoryException.class,
                () -> vault.writeNote("../escaped.md", "nope"));
    }
}
