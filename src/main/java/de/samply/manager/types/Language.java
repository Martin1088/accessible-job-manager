package de.samply.manager.types;

import java.util.Locale;

public enum Language {
    GERMAN(Locale.GERMAN), ENGLISH(Locale.ENGLISH), DUTCH(Locale.forLanguageTag("nl"));

    private final Locale locale;

    Language(Locale locale) {
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }
}
