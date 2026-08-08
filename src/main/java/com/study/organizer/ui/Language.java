package com.study.organizer.ui;

import java.util.Locale;

/**
 * The languages the interface can be shown in.
 *
 * <p>Each one names its own resource bundle suffix ({@code messages_pt.properties}
 * and so on) and the {@link Locale} used for anything Java formats itself, such
 * as weekday names in the charts and the heat map.
 *
 * <p>{@link #nativeName()} is what the language picker in Preferences shows for
 * each entry - always written in that language itself ("Português", not "Portuguese"
 * when the picker is in English) so a user can find their own language even if the
 * one currently active is not one they read.
 */
public enum Language {

    PORTUGUESE("pt", "Português"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español");

    private final String code;
    private final String nativeName;

    Language(String code, String nativeName) {
        this.code = code;
        this.nativeName = nativeName;
    }

    /** @return the resource bundle / preference code, e.g. {@code "pt"} */
    public String code() {
        return code;
    }

    /** @return the locale used for date and weekday formatting in this language */
    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    /**
     * Looks up a language by its stored code, falling back when the code is
     * missing or no longer recognised - for instance a preferences value written
     * by a future version of the application that added a language this one does
     * not know about.
     *
     * @param code the stored code, or {@code null}
     * @return the matching language, or {@link #systemDefault()} if none matches
     */
    public static Language fromCode(String code) {
        for (Language language : values()) {
            if (language.code.equals(code)) {
                return language;
            }
        }
        return systemDefault();
    }

    /**
     * The language to start with when nothing has been chosen yet: the user's
     * operating system language if it is one of the three supported, English
     * otherwise.
     *
     * @return the best default for a first run
     */
    public static Language systemDefault() {
        String systemLanguage = Locale.getDefault().getLanguage();
        for (Language language : values()) {
            if (language.code.equals(systemLanguage)) {
                return language;
            }
        }
        return ENGLISH;
    }

    /**
     * The label shown in the language picker: the language's own name, so it
     * reads correctly regardless of which language is currently active.
     */
    @Override
    public String toString() {
        return nativeName;
    }
}
