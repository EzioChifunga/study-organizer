package com.study.organizer.ui;

import com.study.organizer.model.SessionState;
import com.study.organizer.model.StudySession;
import com.study.organizer.service.FilteredSessionView;
import com.study.organizer.service.PointsCalculator;
import com.study.organizer.service.StudyDataService;
import com.study.organizer.service.TimerService;
import com.study.organizer.vault.ObsidianVault;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the real screens against a real vault in a temporary folder.
 *
 * <p>Compiling proves the code is well formed, but it does not prove the window
 * can actually be constructed. Broken property bindings, a missing stylesheet,
 * or a chart fed bad data only fail at runtime. This test catches all three by
 * building the scene graph for real — it just never shows a window.
 *
 * <p>If no graphics environment is available the tests are skipped rather than
 * failed, since that is an absent display and not a broken application.
 */
class UserInterfaceSmokeTest {

    /** How long to wait for work handed to the JavaFX application thread. */
    private static final int TIMEOUT_SECONDS = 20;

    private static boolean toolkitStarted;

    @TempDir
    Path vaultFolder;

    @BeforeAll
    static void startToolkit() {
        try {
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(ready::countDown);
            toolkitStarted = ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            toolkitStarted = false;
        } catch (UnsupportedOperationException | ExceptionInInitializerError | NoClassDefFoundError e) {
            // No display available (a headless build server, for example).
            toolkitStarted = false;
        }

        Assumptions.assumeTrue(toolkitStarted,
                "JavaFX toolkit unavailable - skipping user interface smoke tests.");
    }

    @AfterAll
    static void stopToolkit() {
        if (toolkitStarted) {
            Platform.exit();
        }
    }

    @Test
    @DisplayName("the whole window builds, with the stylesheet, against a filled vault")
    void buildsEntireWindow() throws Exception {
        StudyDataService data = loadedVault();
        TimerService timer = onFxThread(TimerService::new);

        Scene scene = onFxThread(() -> new MainWindow(timer, data, null).buildScene());

        assertNotNull(scene);
        // Loading the stylesheet is part of the point: a wrong resource path or a
        // stylesheet JavaFX cannot parse would surface right here.
        assertEquals(1, scene.getStylesheets().size());
    }

    @Test
    @DisplayName("applying the stylesheet produces no CSS warnings")
    void stylesheetResolvesCleanly() throws Exception {
        // A looked-up colour that resolves to nothing is not a failure JavaFX
        // will tell you about loudly: it hands the literal text "-color-bg" to
        // the property, logs a warning, and carries on with a broken appearance.
        // Building the scene is therefore not enough to prove the stylesheet is
        // sound - the CSS pass has to actually run and be listened to.
        Logger cssLogger = Logger.getLogger("javafx.css");
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());

        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        cssLogger.setUseParentHandlers(false);
        cssLogger.addHandler(collector);
        try {
            StudyDataService data = loadedVault();
            TimerService timer = onFxThread(TimerService::new);

            // Case 1: no theme class at all. This is the state the window is
            // genuinely in between the scene being built and ThemeManager
            // stamping the class on, and it is where the missing default palette
            // showed up. Stripping the class reproduces that moment exactly.
            onFxThread(() -> {
                Scene scene = new MainWindow(timer, data, "banner").buildScene();
                for (Theme candidate : Theme.values()) {
                    scene.getRoot().getStyleClass().remove(candidate.styleClass());
                }
                scene.getRoot().applyCss();
                return null;
            });

            // Case 2: each theme in turn, because each declares its own palette
            // and a name missing from one would go unnoticed if only the other
            // were tried.
            for (Theme candidate : Theme.values()) {
                onFxThread(() -> {
                    Scene scene = new MainWindow(timer, data, "banner").buildScene();
                    scene.getRoot().getStyleClass().removeAll(
                            Theme.LIGHT.styleClass(), Theme.DARK.styleClass());
                    scene.getRoot().getStyleClass().add(candidate.styleClass());
                    scene.getRoot().applyCss();
                    return null;
                });
            }
        } finally {
            cssLogger.removeHandler(collector);
            cssLogger.setUseParentHandlers(true);
        }

        assertTrue(warnings.isEmpty(),
                "The stylesheet produced CSS warnings:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), warnings));
    }

    @Test
    @DisplayName("the demo window builds, banner and all")
    void buildsDemoWindow() throws Exception {
        StudyDataService data = loadedVault();
        TimerService timer = onFxThread(TimerService::new);

        Scene scene = onFxThread(() ->
                new MainWindow(timer, data, "DEMO MODE - sample data").buildScene());

        assertNotNull(scene.getRoot());
    }

    @Test
    @DisplayName("every screen copes with a completely empty vault")
    void buildsWithNoData() throws Exception {
        StudyDataService empty = new StudyDataService(new ObsidianVault(vaultFolder));
        TimerService timer = onFxThread(TimerService::new);

        assertNotNull(onFxThread(() -> new MainWindow(timer, empty, null).buildScene()));
    }

    @Test
    @DisplayName("the window builds in both themes, and the dial repaints on a switch")
    void buildsInBothThemes() throws Exception {
        StudyDataService data = loadedVault();
        TimerService timer = onFxThread(TimerService::new);

        ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.DARK);

        StopwatchDial dial = onFxThread(() -> new StopwatchDial(timer, theme, 300));
        assertNotNull(dial);
        assertTrue(dial.centreButtonDiameter() > 0);

        // Switching the theme must not throw: the dial repaints from a listener,
        // and a broken colour reference would surface here.
        onFxThread(() -> {
            theme.set(Theme.LIGHT);
            theme.set(Theme.DARK);
            return null;
        });

        for (Theme candidate : Theme.values()) {
            ObjectProperty<Theme> fixed = new SimpleObjectProperty<>(candidate);
            assertNotNull(onFxThread(() -> new DashboardView(timer, data, fixed, () -> { })),
                    "The dashboard failed to build in the " + candidate + " theme.");
        }
    }

    @Test
    @DisplayName("the heat map uses a different scale in each theme")
    void heatMapFollowsTheTheme() throws Exception {
        StudyDataService data = loadedVault();

        Map<LocalDate, Long> days = data.getStatistics().getHeatMapData(4);

        // Both must build; a colour missing from one theme would fail here.
        for (Theme candidate : Theme.values()) {
            assertNotNull(onFxThread(() -> new HeatMapPane(days, candidate)),
                    "The heat map failed to build in the " + candidate + " theme.");
        }

        // The two scales must actually differ, or the map would be unreadable in
        // one of them - a light grey empty square is invisible on a dark panel.
        assertNotEquals(Theme.LIGHT.heatEmpty(), Theme.DARK.heatEmpty(),
                "Both themes use the same empty-day colour.");
        assertNotEquals(Theme.LIGHT.heatFull(), Theme.DARK.heatFull(),
                "Both themes use the same busiest-day colour.");

        // And within a theme the two ends must be distinguishable from each other.
        for (Theme candidate : Theme.values()) {
            assertNotEquals(candidate.heatEmpty(), candidate.heatFull(),
                    candidate + " cannot tell a studied day from an empty one.");
        }
    }

    @Test
    @DisplayName("the charts screen redraws when the theme is switched")
    void chartsFollowTheTheme() throws Exception {
        StudyDataService data = loadedVault();
        ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.DARK);

        FilteredSessionView view = viewOf(data);
        ChartsPane charts = onFxThread(() -> new ChartsPane(view, theme));
        assertNotNull(charts);

        // The heat map paints in code, so switching must rebuild it rather than
        // leaving the old colours on screen. A throw here means it does not.
        onFxThread(() -> {
            theme.set(Theme.LIGHT);
            theme.set(Theme.DARK);
            return null;
        });
    }

    @Test
    @DisplayName("an untouched filter grid hides nothing")
    void filtersStartOpen() throws Exception {
        StudyDataService data = loadedVault();

        SessionFilterPane filters = onFxThread(() -> new SessionFilterPane(data));

        long all = data.getSessions().stream()
                .filter(filters.predicateProperty().get())
                .count();
        assertEquals(data.getSessions().size(), all,
                "An untouched filter grid must hide nothing.");

        assertNull(filters.getSelectedCategory(),
                "No category should be filtered to begin with.");
    }

    @Test
    @DisplayName("filtering narrows the table, the charts and the statistics together")
    void oneFilterDrivesEveryScreen() throws Exception {
        StudyDataService data = loadedVault();

        // The test vault holds two Java sessions and one Algorithms session.
        SimpleObjectProperty<Predicate<StudySession>> predicate =
                new SimpleObjectProperty<>(session -> true);

        FilteredSessionView view =
                new FilteredSessionView(data.getSessions(), predicate);

        assertEquals(data.getSessions().size(), view.getSessions().size());
        assertEquals(data.getSessions().size(), view.getStatistics().getTotalSessions(),
                "The statistics must start out describing everything.");

        // Narrow to one category, exactly as the category box would.
        predicate.set(session -> "Java".equals(session.getCategory()));

        assertEquals(2, view.getSessions().size(),
                "The table's list did not follow the filter.");

        // This is the point of the shared bar: the figures the charts and the
        // statistics read must move with the table, not lag behind it.
        assertEquals(2, view.getStatistics().getTotalSessions(),
                "The statistics disagree with the table.");
        assertEquals(
                view.getSessions().stream().mapToLong(StudySession::getDurationSeconds).sum(),
                view.getStatistics().getTotalSeconds(),
                "The total time disagrees with the sessions in view.");
        assertEquals(1, view.getStatistics().getSecondsByCategory().size(),
                "The category chart still shows a filtered-out category.");

        // And deleting a session while a filter is active must still reach both.
        String doomedId = view.getSessions().get(0).getId();
        CountDownLatch deleted = new CountDownLatch(1);
        Platform.runLater(() -> data.deleteSession(
                doomedId, deleted::countDown, error -> deleted.countDown()));
        assertTrue(deleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertEquals(1, view.getSessions().size());
        assertEquals(1, view.getStatistics().getTotalSessions(),
                "The statistics did not notice a session being deleted.");
    }

    @Test
    @DisplayName("every theme defines every colour the dial needs")
    void themesAreComplete() {
        for (Theme candidate : Theme.values()) {
            assertNotNull(candidate.dialFace(), candidate + " is missing a dial face colour.");
            assertNotNull(candidate.minorTick(), candidate + " is missing a minor tick colour.");
            assertNotNull(candidate.majorTick(), candidate + " is missing a major tick colour.");
            assertNotNull(candidate.rimNumber(), candidate + " is missing a rim number colour.");
            assertNotNull(candidate.secondHand(), candidate + " is missing a second hand colour.");
            assertNotNull(candidate.minuteHand(), candidate + " is missing a minute hand colour.");
            assertNotNull(candidate.centreDisc(), candidate + " is missing a centre disc colour.");
            assertNotNull(candidate.centreDiscEdge(), candidate + " is missing a disc edge colour.");

            assertNotEquals(candidate, candidate.opposite(),
                    "A theme's opposite must be a different theme.");
        }
    }

    @Test
    @DisplayName("the history screen builds and its editor opens for both jobs")
    void buildsHistoryScreen() throws Exception {
        StudyDataService data = loadedVault();

        SessionFilterPane filters = onFxThread(() -> new SessionFilterPane(data));
        FilteredSessionView view = new FilteredSessionView(
                data.getSessions(), filters.predicateProperty());

        assertNotNull(onFxThread(() -> new HistoryPane(data, filters, view)));
        assertFalse(data.getSessions().isEmpty(), "The history table has nothing to show.");

        StudySession existing = data.getSessions().get(0);
        assertNotNull(onFxThread(() -> new SessionEditorDialog(data)));
        assertNotNull(onFxThread(() -> new SessionEditorDialog(existing, data)));
    }

    @Test
    @DisplayName("the note picker offers only the chosen category's notes")
    void notePickerIsScopedToOneFolder() throws Exception {
        StudyDataService data = loadedVault();

        NotePickerPane picker = onFxThread(() -> new NotePickerPane(data));

        onFxThread(() -> {
            picker.setCategory("Java");
            picker.setSelected(List.of("Notes on generics", "Big O reference"));
            return null;
        });

        // "Big O reference" is in the Algorithms folder, so it must be dropped
        // rather than written as a link that leaves the folder.
        assertEquals(List.of("Notes on generics"), picker.getSelected(),
                "The picker must refuse a note from another category.");

        // Switching folders must clear a selection that no longer applies.
        onFxThread(() -> {
            picker.setCategory("Algorithms");
            return null;
        });
        assertTrue(picker.getSelected().isEmpty(),
                "Changing category must drop references that are now out of folder.");
    }

    @Test
    @DisplayName("a session cannot be offered its own note as a reference")
    void aSessionCannotReferenceItself() throws Exception {
        StudyDataService data = loadedVault();

        StudySession session = data.getSessions().stream()
                .filter(candidate -> "Java".equals(candidate.getCategory()))
                .findFirst()
                .orElseThrow();

        // The session's own note lives in the folder it links into, so without
        // an exclusion the picker offers it and the session links to itself -
        // a reference that means nothing and loops in the note viewer.
        String ownTitle = session.getId()
                .substring(session.getId().lastIndexOf('/') + 1)
                .replaceFirst("\\.md$", "");

        NotePickerPane picker = onFxThread(() -> new NotePickerPane(data));

        onFxThread(() -> {
            picker.setExcludedNote(ownTitle);
            picker.setCategory("Java");
            // Ticking it must have no effect, because it is not on offer.
            picker.setSelected(List.of(ownTitle, "Notes on generics"));
            return null;
        });

        assertFalse(picker.getSelected().contains(ownTitle),
                "The picker let a session reference its own note.");
        assertEquals(List.of("Notes on generics"), picker.getSelected());
    }

    @Test
    @DisplayName("the note viewer opens a session note and finds its links")
    void noteViewerOpensNotes() throws Exception {
        StudyDataService data = loadedVault();

        StudySession withReference = data.getSessions().stream()
                .filter(session -> !session.getReferences().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("The test vault has no referencing session."));

        NoteViewerPane viewer = onFxThread(() -> new NoteViewerPane(data));

        onFxThread(() -> {
            viewer.show(withReference.getId());
            viewer.clear();
            return null;
        });

        // Following the link must land on a real note in the same folder.
        String target = data.resolveLink(
                withReference.getCategory(), withReference.getReferences().get(0));
        assertNotNull(target, "The reference did not resolve to a note.");
        assertTrue(target.startsWith(withReference.getCategory() + "/"),
                "A reference must resolve inside its own category folder.");
        assertNotNull(data.readNote(target));
    }

    @Test
    @DisplayName("editing a session replaces it instead of adding a duplicate")
    void editingReplacesTheRecord() throws Exception {
        StudyDataService data = loadedVault();

        int before = data.getSessions().size();
        StudySession original = data.getSessions().get(0);

        StudySession corrected = new StudySession(
                original.getId(),
                original.getCategory(),
                original.getStartedAt(),
                original.getEndedAt(),
                original.getDurationSeconds(),
                original.getPoints(),
                "Corrected summary",
                "Corrected observations",
                original.getReferences());

        CountDownLatch saved = new CountDownLatch(1);
        Platform.runLater(() -> data.updateSession(
                corrected, saved::countDown, error -> saved.countDown()));
        assertTrue(saved.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertEquals(before, data.getSessions().size(),
                "Editing must not change how many sessions there are.");
        assertTrue(data.getSessions().stream()
                        .anyMatch(s -> "Corrected summary".equals(s.getSummary())),
                "The edit did not reach the vault.");
    }

    @Test
    @DisplayName("deleting a session removes it from every view")
    void deletingRemovesTheRecord() throws Exception {
        StudyDataService data = loadedVault();

        int before = data.getSessions().size();
        String doomedId = data.getSessions().get(0).getId();

        CountDownLatch deleted = new CountDownLatch(1);
        Platform.runLater(() -> data.deleteSession(
                doomedId, deleted::countDown, error -> deleted.countDown()));
        assertTrue(deleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertEquals(before - 1, data.getSessions().size());
        assertTrue(data.getSessions().stream().noneMatch(s -> doomedId.equals(s.getId())));

        // The statistics snapshot is rebuilt by the same code path, so it must
        // agree with the table rather than lagging a step behind.
        assertEquals(data.getSessions().size(), data.getStatistics().getTotalSessions());
    }

    @Test
    @DisplayName("the timer runs through its whole lifecycle")
    void runsTimerLifecycle() throws Exception {
        TimerService timer = onFxThread(TimerService::new);

        onFxThread(() -> {
            assertEquals(SessionState.IDLE, timer.getState());

            timer.start("Java");
            assertEquals(SessionState.RUNNING, timer.getState());

            // Starting twice must be refused - only one session at a time.
            assertThrows(IllegalStateException.class, () -> timer.start("Math"));

            timer.pause();
            assertEquals(SessionState.PAUSED, timer.getState());

            timer.resume();
            assertEquals(SessionState.RUNNING, timer.getState());

            // A summary is mandatory.
            assertThrows(IllegalArgumentException.class, () -> timer.finish("   ", ""));

            StudySession finished = timer.finish("Learned interfaces", "Revisit generics");
            assertEquals("Java", finished.getCategory());
            assertEquals("Learned interfaces", finished.getSummary());
            assertTrue(finished.getDurationSeconds() >= 0);

            timer.reset();
            assertEquals(SessionState.IDLE, timer.getState());

            // Nothing to cancel once idle.
            assertThrows(IllegalStateException.class, timer::cancel);
            return null;
        });
    }

    // ------------------------------------------------------------- fixtures

    /**
     * Builds a vault with two categories, some background notes and a few
     * sessions, then waits for the data service to have read it.
     */
    private StudyDataService loadedVault() throws IOException, InterruptedException {
        ObsidianVault vault = new ObsidianVault(vaultFolder);

        vault.writeNote("Java/Notes on generics.md", "# Notes on generics\n");
        vault.writeNote("Java/Streams API.md", "# Streams API\n");
        vault.writeNote("Algorithms/Big O reference.md", "# Big O reference\n");

        var repository = new com.study.organizer.repository.VaultSessionRepository(
                vault, ZoneId.systemDefault());

        Instant now = Instant.now();
        repository.save(sampleSession(now, 3600, "Java", List.of("Notes on generics")));
        repository.save(sampleSession(now.minus(1, ChronoUnit.DAYS), 1800, "Java", List.of()));
        repository.save(sampleSession(now.minus(2, ChronoUnit.DAYS), 5400, "Algorithms", List.of()));

        StudyDataService data = new StudyDataService(vault);

        // reload() does its work on a background thread and publishes the result
        // back on the application thread, so there is no point at which simply
        // queueing another runLater would prove the load has finished. Wait for
        // the session list itself to fill instead.
        CountDownLatch loaded = new CountDownLatch(1);
        data.getSessions().addListener(
                (ListChangeListener<StudySession>) change -> loaded.countDown());

        Platform.runLater(() -> data.reload(error -> loaded.countDown()));

        assertTrue(loaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "The vault never finished loading.");
        return data;
    }

    /** An unfiltered view over a data service, for the screens that need one. */
    private static FilteredSessionView viewOf(StudyDataService data) {
        return new FilteredSessionView(
                data.getSessions(), new SimpleObjectProperty<>(session -> true));
    }

    private static StudySession sampleSession(Instant start, long seconds,
                                              String category, List<String> references) {
        return new StudySession(
                null, category, start, start.plusSeconds(seconds), seconds,
                PointsCalculator.fromSeconds(seconds), "Sample summary", "", references);
    }

    /**
     * Runs work on the JavaFX application thread and returns its result.
     *
     * <p>Scene-graph objects may only be created there, so the tests cannot
     * simply call the constructors directly.
     *
     * @param work what to build
     * @param <T>  the type produced
     * @return the result of the work
     * @throws Exception if the work threw, or the thread did not respond in time
     */
    private static <T> T onFxThread(Supplier<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                result.set(work.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "The JavaFX application thread did not respond in time.");

        if (failure.get() != null) {
            throw new AssertionError("Building the user interface failed.", failure.get());
        }
        return result.get();
    }
}
