package com.study.organizer.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;

import java.util.prefs.Preferences;

/**
 * Holds the theme currently in use, applies it to the window, and remembers the
 * user's choice between runs.
 *
 * <p>The theme is exposed as an observable property, so anything that needs to
 * repaint on a change — the stopwatch dial, for instance — can simply listen to
 * it rather than being told about the change by whoever flipped the switch.
 *
 * <p>The choice is stored with {@link Preferences}, the small key-value store
 * the JDK provides for exactly this kind of setting. On Windows that is a
 * registry key; the API hides the difference. No file to manage and nothing to
 * add to the build.
 */
public class ThemeManager {

    /** The preference key under which the chosen theme is stored. */
    private static final String PREFERENCE_KEY = "theme";

    /**
     * The theme currently in use, kept statically so dialogs can find it.
     *
     * <p>A dialog is its own window with its own scene, so it does not inherit
     * the main window's styling and has to be told which theme to use. Passing a
     * {@link ThemeManager} into every dialog would thread one parameter through
     * a dozen call sites for a purely cosmetic concern. Since the application
     * has exactly one window and one theme at a time, a static reference is the
     * honest description of the situation rather than a shortcut around it.
     */
    private static Theme activeTheme = Theme.DARK;

    private final Preferences preferences = Preferences.userNodeForPackage(ThemeManager.class);

    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(loadSavedTheme());

    /**
     * Applies the current theme to a scene, and keeps applying it on every change.
     *
     * <p>Switching works by swapping a single style class on the scene's root
     * node. The stylesheet defines its colours twice — once under
     * {@code .theme-light} and once under {@code .theme-dark} — so every control
     * in the window restyles itself at once, with no need to walk the scene
     * graph.
     *
     * @param scene the scene to style
     */
    public void applyTo(Scene scene) {
        applyToRoot(scene.getRoot(), theme.get());
        theme.addListener((observable, old, current) -> {
            applyToRoot(scene.getRoot(), current);
            save(current);
        });
    }

    private static void applyToRoot(Parent root, Theme current) {
        activeTheme = current;

        // Remove every theme class first, so the classes cannot accumulate as
        // the user toggles back and forth.
        for (Theme candidate : Theme.values()) {
            root.getStyleClass().remove(candidate.styleClass());
        }
        root.getStyleClass().add(current.styleClass());
    }

    /**
     * Styles a dialog to match the main window.
     *
     * <p>Every dialog in the application calls this, so a pop-up never appears
     * in the wrong theme.
     *
     * @param pane the dialog pane to style
     */
    public static void styleDialog(DialogPane pane) {
        stylePopup(pane);
    }

    /**
     * Styles a node that lives in its own window.
     *
     * <p>A dialog or a context menu is a separate scene with its own root, so it
     * cannot see the main window's stylesheet or its theme class. Both have to be
     * attached directly, or the pop-up appears in the default JavaFX theme —
     * bright white in the middle of a dark window.
     *
     * <p>This is also why the stylesheet's palette selectors are written as
     * {@code .theme-dark} rather than {@code .root.theme-dark}: these nodes carry
     * the theme class but are not always the scene root.
     *
     * @param node the pop-up's top-level node
     */
    public static void stylePopup(Parent node) {
        var stylesheet = ThemeManager.class.getResource("/styles.css");
        if (stylesheet != null && !node.getStylesheets().contains(stylesheet.toExternalForm())) {
            node.getStylesheets().add(stylesheet.toExternalForm());
        }

        for (Theme candidate : Theme.values()) {
            node.getStyleClass().remove(candidate.styleClass());
        }
        node.getStyleClass().add(activeTheme.styleClass());
    }

    /**
     * Styles a context menu to match the window.
     *
     * <p>A {@link ContextMenu} is not a {@link Parent}, so it cannot be passed to
     * {@link #stylePopup(Parent)} — its style classes are set on the menu itself
     * and its stylesheet reached through its scene.
     *
     * @param menu the menu to style
     */
    public static void styleContextMenu(ContextMenu menu) {
        var stylesheet = ThemeManager.class.getResource("/styles.css");
        if (stylesheet != null) {
            menu.getScene().getStylesheets().add(stylesheet.toExternalForm());
        }
        menu.getStyleClass().add(activeTheme.styleClass());
    }

    /** Switches to the other theme. */
    public void toggle() {
        theme.set(theme.get().opposite());
    }

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public Theme getTheme() {
        return theme.get();
    }

    /**
     * Reads the saved choice, defaulting to dark.
     *
     * <p>Dark is the default because it is what the stopwatch face was designed
     * around, and it is the kinder option for studying at night.
     *
     * @return the stored theme, or {@link Theme#DARK} if none was stored
     */
    private Theme loadSavedTheme() {
        String stored = preferences.get(PREFERENCE_KEY, Theme.DARK.name());
        try {
            return Theme.valueOf(stored);
        } catch (IllegalArgumentException e) {
            // The stored value is not a theme we recognise any more. Fall back
            // rather than refusing to start over a cosmetic setting.
            return Theme.DARK;
        }
    }

    private void save(Theme current) {
        preferences.put(PREFERENCE_KEY, current.name());
    }
}
