package com.study.organizer.ui;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Looks up interface text in the language the user has chosen, and remembers
 * that choice between runs.
 *
 * <p>Modelled on {@link ThemeManager}: a small static store backed by
 * {@link Preferences}, the JDK's key-value store, so there is no file to manage
 * and nothing to add to the build. Where the theme swaps a CSS class on the fly,
 * a language swap needs every screen rebuilt with the new bundle's strings — see
 * {@link MainWindow#rebuildForLanguageChange()} - so the choice takes effect the
 * next time the window (or the whole application) opens rather than live.
 *
 * <h2>Why placeholders are {@code {0}}, {@code {1}} ... rather than
 * {@link java.text.MessageFormat}</h2>
 * Every value already arrives pre-formatted - a duration, a category name, a
 * count - so there is nothing for {@code MessageFormat} to format itself, only
 * substitution to do. Doing that with plain {@link String#replace} avoids its
 * gotcha of treating a bare apostrophe as the start of a quoted literal, which
 * would otherwise silently eat text the moment a translation used one.
 */
public final class I18n {

    private static final String PREFERENCE_KEY = "language";
    private static final String BUNDLE_NAME = "i18n.messages";

    private static volatile Language currentLanguage = loadSavedLanguage();
    private static volatile ResourceBundle bundle = loadBundle(currentLanguage);

    private I18n() {
        throw new AssertionError("I18n is a utility class and cannot be instantiated.");
    }

    /** @return the language currently in use */
    public static Language getLanguage() {
        return currentLanguage;
    }

    /**
     * Switches the active language and remembers the choice for next time.
     *
     * @param language the language to switch to
     */
    public static void setLanguage(Language language) {
        currentLanguage = language;
        bundle = loadBundle(language);
        Preferences.userNodeForPackage(I18n.class).put(PREFERENCE_KEY, language.code());
    }

    /**
     * Looks up a piece of interface text.
     *
     * @param key the key from {@code messages_*.properties}
     * @return the translated text, or the key itself if it is missing from the
     *         bundle - visibly wrong rather than silently blank, which is what
     *         makes a missing translation easy to spot
     */
    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Looks up a piece of interface text and fills in its placeholders.
     *
     * <p>{@code {0}} is replaced with {@code args[0]}, {@code {1}} with
     * {@code args[1]}, and so on. A translation is free to use a placeholder more
     * than once, drop one, or reorder them - whatever reads naturally in that
     * language.
     *
     * @param key  the key from {@code messages_*.properties}
     * @param args the values to substitute
     * @return the translated, filled-in text
     */
    public static String t(String key, Object... args) {
        String text = t(key);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    private static ResourceBundle loadBundle(Language language) {
        return ResourceBundle.getBundle(BUNDLE_NAME, language.locale());
    }

    private static Language loadSavedLanguage() {
        String stored = Preferences.userNodeForPackage(I18n.class).get(PREFERENCE_KEY, null);
        return stored == null ? Language.systemDefault() : Language.fromCode(stored);
    }
}
