package de.samply.manager.types;

public enum SharedSubject {
    PROFILE_BASICS,
    APPLICATION_REVIEW,
    APPLICATION_STATUS,
    COMPANIES,
    DOCUMENT,
    HTML_LETTER_TEMPLATE;

    public boolean isPerResource() {
        return this == DOCUMENT || this == HTML_LETTER_TEMPLATE;
    }
}
