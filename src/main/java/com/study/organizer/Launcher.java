package com.study.organizer;

/**
 * The entry point used by the packaged application.
 *
 * <h2>Why this class has to exist</h2>
 * When Java is asked to run a class that extends {@link javafx.application.Application}
 * directly, it first checks that the JavaFX <i>modules</i> are on the module
 * path. In the packaged build they are on the ordinary class path instead, so
 * that check fails with the unhelpful message:
 *
 * <pre>
 *   Error: JavaFX runtime components are missing, and are required to run this application
 * </pre>
 *
 * <p>The check only applies to the class named as the entry point. A plain class
 * that does not extend {@code Application} is therefore started without
 * complaint, and can go on to launch the real one — by which time JavaFX is
 * already loaded from the class path and everything works.
 *
 * <p>The alternative is to make the project a Java module and use {@code jlink},
 * which is the more modern route but means a {@code module-info.java} and a
 * modular build. For a single-window application this one small class is the
 * simpler trade.
 *
 * <p>Running from source with {@code ./mvnw javafx:run} does not need this at
 * all — the plugin puts JavaFX on the module path itself — so {@link MainApp}
 * remains a perfectly good entry point during development.
 */
public final class Launcher {

    private Launcher() {
        throw new AssertionError("Launcher is an entry point and cannot be instantiated.");
    }

    /**
     * Starts the application.
     *
     * @param args passed straight through to {@link MainApp}
     */
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
