package de.samply.manager.jobimport.llm;

/** Prompt text and output guards shared by every JobPostingLlmClient implementation. */
final class JobPostingLlmPrompt {

    static final String SYSTEM_PROMPT =
            "Extract job posting fields. Only fill fields present in the text. " +
            "Use null for missing. Extract values in the source language. " +
            "Each field is a short value - a name or phrase, never a sentence or paragraph. " +
            "'company' is only the employer's name, not an address, slogan, or description.";

    /**
     * Small models occasionally spill unrelated page text into a field
     * instead of the short value it's meant to hold. Cap defensively so a
     * runaway field can't silently violate a downstream varchar(255) column.
     */
    static final int MAX_FIELD_LENGTH = 200;

    static String truncate(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() > MAX_FIELD_LENGTH ? value.substring(0, MAX_FIELD_LENGTH) : value;
    }

    private JobPostingLlmPrompt() {
    }
}
