package com.study.organizer.demo;

import com.study.organizer.model.StudySession;
import com.study.organizer.repository.VaultSessionRepository;
import com.study.organizer.vault.ObsidianVault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Builds a throwaway vault full of invented notes, for the demo application.
 *
 * <p>The demo writes <b>real Markdown files into a real vault</b> in a temporary
 * folder, rather than pretending to store things in memory. That matters: it
 * means the demo exercises exactly the same reading, writing, linking and
 * moving code as the real application, so anything broken there is broken in the
 * demo too and gets noticed.
 *
 * <p>The folder is deleted when the application exits.
 */
public final class DemoVault {

    /**
     * A few ordinary notes per category, so the reference picker has something
     * to offer and the link navigation has somewhere to go.
     *
     * <p>Without these, the demo would show sessions with no references at all
     * and the most interesting part of the screen would look empty.
     */
    private static final Map<String, List<String>> BACKGROUND_NOTES = Map.of(
            "Java", List.of(
                    "Notes on generics",
                    "Interfaces and abstract classes",
                    "Collections cheatsheet",
                    "Streams API"),
            "Algorithms", List.of(
                    "Big O reference",
                    "Sorting algorithms compared",
                    "Graph traversal"),
            "Databases", List.of(
                    "Normal forms",
                    "SQL joins"),
            "English", List.of(
                    "Vocabulary log",
                    "Phrasal verbs"));

    private DemoVault() {
        throw new AssertionError("DemoVault is a utility class and cannot be instantiated.");
    }

    /**
     * Creates a temporary vault and fills it with sample content.
     *
     * @return the vault, ready to use
     * @throws IOException if the temporary folder cannot be created
     */
    public static ObsidianVault create() throws IOException {
        Path folder = Files.createTempDirectory("study-organizer-demo-vault");
        deleteOnExit(folder);

        ObsidianVault vault = new ObsidianVault(folder);
        writeBackgroundNotes(vault);
        writeSessions(vault);

        return vault;
    }

    /** Writes the ordinary, non-session notes that sessions can link to. */
    private static void writeBackgroundNotes(ObsidianVault vault) {
        BACKGROUND_NOTES.forEach((category, titles) -> {
            vault.ensureCategory(category);
            for (String title : titles) {
                vault.writeNote(category + "/" + title + ".md",
                        "# " + title + System.lineSeparator()
                                + System.lineSeparator()
                                + "Sample note for the demo. In your own vault this would be "
                                + "whatever you actually wrote about "
                                + title.toLowerCase() + "." + System.lineSeparator());
            }
        });
    }

    /**
     * Writes the invented sessions, giving some of them references.
     *
     * <p>References are only ever chosen from the session's own category folder,
     * which is the same rule the application enforces everywhere else.
     */
    private static void writeSessions(ObsidianVault vault) {
        VaultSessionRepository repository =
                new VaultSessionRepository(vault, ZoneId.systemDefault());

        List<StudySession> sessions = SampleData.generate(ZoneId.systemDefault());

        int index = 0;
        for (StudySession session : sessions) {
            List<String> available =
                    BACKGROUND_NOTES.getOrDefault(session.getCategory(), List.of());

            // Roughly every other session gets a reference, so the history shows
            // both kinds of record.
            List<String> references = available.isEmpty() || index % 2 == 1
                    ? List.of()
                    : List.of(available.get(index / 2 % available.size()));

            repository.save(new StudySession(
                    null,
                    session.getCategory(),
                    session.getStartedAt(),
                    session.getEndedAt(),
                    session.getDurationSeconds(),
                    session.getPoints(),
                    session.getSummary(),
                    session.getObservations(),
                    references));

            index++;
        }
    }

    /**
     * Removes the temporary vault when the JVM exits.
     *
     * <p>{@code File.deleteOnExit} only handles empty folders, so the whole tree
     * is walked and deleted from a shutdown hook instead.
     *
     * @param folder the folder to clean up
     */
    private static void deleteOnExit(Path folder) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                            throws IOException {
                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                // Exiting anyway; the operating system clears its temp folder.
                System.err.println("Could not remove the demo vault: " + e.getMessage());
            }
        }, "demo-vault-cleanup"));
    }

    /** @return the character set notes are written in, documented for the README */
    public static String encoding() {
        return StandardCharsets.UTF_8.name();
    }
}
