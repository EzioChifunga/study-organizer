package com.study.organizer.service;

import com.study.organizer.model.StudySession;
import com.study.organizer.repository.CategoryRepository;
import com.study.organizer.repository.SessionRepository;
import com.study.organizer.repository.VaultCategoryRepository;
import com.study.organizer.repository.VaultSessionRepository;
import com.study.organizer.vault.ObsidianVault;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Holds the application's loaded data and mediates every trip to the database.
 *
 * <h2>Why this class exists</h2>
 * Reading and writing the vault touches the disk and can take anything from a few
 * milliseconds to several seconds. If they ran on the JavaFX application thread
 * the entire window would freeze while they waited — no repainting, no button
 * presses. So every database call here is handed to a background thread, and
 * only the <i>result</i> is pushed back onto the application thread with
 * {@link Platform#runLater(Runnable)}.
 *
 * <p>That rule — <b>touch the UI only from the application thread</b> — is not
 * optional in JavaFX; breaking it causes crashes that are hard to reproduce.
 *
 * <p>The loaded data is exposed as a {@link StatisticsService} snapshot. Views
 * listen to {@link #statisticsProperty()} and redraw themselves whenever it is
 * replaced, so no view ever has to know when or why the data changed.
 */
public class StudyDataService {

    private final SessionRepository sessionRepository;
    private final CategoryRepository categoryRepository;

    /**
     * The vault, for the note-level work the screens need beyond whole sessions:
     * searching a folder's notes by title, reading a note to display it, and
     * following a link.
     *
     * <p>The repositories above stay as interfaces because session logic is worth
     * being able to test against a stand-in. Note browsing is different — it is
     * inherently about files in folders, and pretending otherwise would add a
     * layer that describes nothing.
     */
    private final ObsidianVault vault;

    /**
     * The thread that runs database work.
     *
     * <p>A single thread rather than a pool: the operations here are few and
     * naturally sequential, and one thread means a save can never overtake the
     * reload that follows it.
     *
     * <p>The thread is a <b>daemon</b> thread so it cannot keep the JVM alive
     * after the window closes.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "study-organizer-db");
        thread.setDaemon(true);
        return thread;
    });

    /** The latest snapshot of all statistics. Replaced wholesale on every reload. */
    private final ReadOnlyObjectWrapper<StatisticsService> statistics =
            new ReadOnlyObjectWrapper<>(new StatisticsService(List.of()));

    /**
     * Every saved session, newest first.
     *
     * <p>The history table is bound straight to this list, so it redraws itself
     * whenever the contents change without any refresh call.
     */
    private final ObservableList<StudySession> sessions = FXCollections.observableArrayList();

    /** Known category names, for the drop-down on the timer screen. */
    private final ObservableList<String> categories = FXCollections.observableArrayList();

    /** True while a database operation is in flight, so the UI can show a busy state. */
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);

    /**
     * @param sessionRepository  where sessions are stored
     * @param categoryRepository where categories are stored
     * @param vault              the vault behind them, for note-level browsing
     */
    public StudyDataService(SessionRepository sessionRepository,
                            CategoryRepository categoryRepository,
                            ObsidianVault vault) {
        this.sessionRepository = sessionRepository;
        this.categoryRepository = categoryRepository;
        this.vault = vault;
    }

    /**
     * Builds a data service reading and writing the given vault.
     *
     * @param vault the vault to use
     */
    public StudyDataService(ObsidianVault vault) {
        this(new VaultSessionRepository(vault), new VaultCategoryRepository(vault), vault);
    }

    /** @return the vault behind this service */
    public ObsidianVault getVault() {
        return vault;
    }

    /**
     * The titles of every note in one category folder.
     *
     * <p>This is what the reference picker offers. Because the vault only ever
     * lists a single folder, a session cannot be given a link that leaves its
     * category — the rule holds because of what the user is shown, not because
     * of a check somewhere that could be missed.
     *
     * @param category the category whose folder to list
     * @return the note titles, alphabetically
     */
    public List<String> listNoteTitles(String category) {
        return vault.listNoteTitles(category);
    }

    /**
     * Reads a note's raw Markdown, for display.
     *
     * @param relativePath the note's path relative to the vault root
     * @return the note's text
     */
    public String readNote(String relativePath) {
        return vault.readNote(relativePath);
    }

    /**
     * Follows a wiki link within a category folder.
     *
     * @param category the folder the link was written in
     * @param title    the title inside the {@code [[...]]}
     * @return the target note's path, or {@code null} if the link is broken
     */
    public String resolveLink(String category, String title) {
        return vault.resolveLink(category, title);
    }

    /**
     * Reloads all sessions and categories from the vault in the background.
     *
     * @param onError called on the application thread if the load fails
     */
    public void reload(Consumer<Throwable> onError) {
        busy.set(true);

        runInBackground(this::loadEverything, this::publish, onError);
    }

    /**
     * Saves a finished session, then reloads so every view reflects it.
     *
     * @param session   the session to save
     * @param onSuccess called on the application thread once the save succeeded
     * @param onError   called on the application thread if the save failed
     */
    public void saveSession(StudySession session, Runnable onSuccess, Consumer<Throwable> onError) {
        busy.set(true);

        runInBackground(
                () -> {
                    // Remember the category first. If this succeeded but the session
                    // save then failed, the only consequence is an unused name in the
                    // drop-down — harmless, and far better than losing the session.
                    categoryRepository.ensureExists(session.getCategory());
                    sessionRepository.save(session);
                    return loadEverything();
                },
                result -> {
                    publish(result);
                    onSuccess.run();
                },
                onError);
    }

    /**
     * Saves a corrected version of an existing session, then reloads.
     *
     * @param session   the session to overwrite; must already have an id
     * @param onSuccess called on the application thread once the save succeeded
     * @param onError   called on the application thread if the save failed
     */
    public void updateSession(StudySession session, Runnable onSuccess, Consumer<Throwable> onError) {
        busy.set(true);

        runInBackground(
                () -> {
                    // An edit can introduce a category that did not exist before.
                    categoryRepository.ensureExists(session.getCategory());
                    sessionRepository.update(session);
                    return loadEverything();
                },
                result -> {
                    publish(result);
                    onSuccess.run();
                },
                onError);
    }

    /**
     * Permanently removes a session, then reloads.
     *
     * @param id        the id of the session to remove
     * @param onSuccess called on the application thread once the delete succeeded
     * @param onError   called on the application thread if the delete failed
     */
    public void deleteSession(String id, Runnable onSuccess, Consumer<Throwable> onError) {
        busy.set(true);

        runInBackground(
                () -> {
                    sessionRepository.delete(id);
                    return loadEverything();
                },
                result -> {
                    publish(result);
                    onSuccess.run();
                },
                onError);
    }

    /** Reads both collections. Runs on the background thread. */
    private LoadResult loadEverything() {
        return new LoadResult(sessionRepository.findAll(), categoryRepository.findAll());
    }

    /**
     * Pushes a freshly loaded result into the observable state.
     *
     * <p>Always called on the application thread, and every operation ends here.
     * That is what keeps the history table, the charts and the dashboard from
     * ever disagreeing about what is stored.
     */
    private void publish(LoadResult result) {
        sessions.setAll(result.sessions());
        categories.setAll(result.categories());
        statistics.set(new StatisticsService(result.sessions()));
    }

    /**
     * Runs work on the background thread and delivers the outcome on the
     * application thread.
     *
     * <p>This is the one place that crosses the thread boundary, which is why
     * the {@code Platform.runLater} calls all live here rather than being
     * scattered through the views.
     *
     * @param work      the database work to perform
     * @param onSuccess what to do with the result, on the application thread
     * @param onError   what to do with a failure, on the application thread
     * @param <T>       the type of result the work produces
     */
    private <T> void runInBackground(BackgroundWork<T> work,
                                     Consumer<T> onSuccess,
                                     Consumer<Throwable> onError) {
        executor.submit(() -> {
            try {
                T result = work.run();
                Platform.runLater(() -> {
                    busy.set(false);
                    onSuccess.accept(result);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    busy.set(false);
                    onError.accept(e);
                });
            }
        });
    }

    /** Stops the background thread. Called when the application shuts down. */
    public void shutdown() {
        executor.shutdownNow();
    }

    public ReadOnlyObjectProperty<StatisticsService> statisticsProperty() {
        return statistics.getReadOnlyProperty();
    }

    public StatisticsService getStatistics() {
        return statistics.get();
    }

    public ObservableList<String> getCategories() {
        return categories;
    }

    /**
     * Every saved session, newest first.
     *
     * @return the live list the history table is bound to
     */
    public ObservableList<StudySession> getSessions() {
        return sessions;
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    /** A unit of database work that may fail. */
    @FunctionalInterface
    private interface BackgroundWork<T> {
        T run() throws Exception;
    }

    /** The two lists a reload produces, carried back to the UI thread together. */
    private record LoadResult(List<StudySession> sessions, List<String> categories) {
    }
}
