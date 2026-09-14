package de.samply.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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

    /**
     * A client-rendered page (Comeet, some Workday tenants) can serve its
     * template literally - {@code {{position.name}}}, {@code ${job.title}} - as
     * the only "text" on the page, and a small model asked to extract from that
     * copies the placeholder straight into the field. No real posting field
     * value contains a mustache- or JSP-style placeholder, so one means "not
     * found" rather than a value.
     */
    private static final Pattern UNRENDERED_PLACEHOLDER =
            Pattern.compile("\\{\\{.+?}}|\\$\\{.+?}");

    /**
     * Applies the cap to every string the model returned, so a spec gets the
     * guard by existing rather than by each provider remembering to call it
     * field by field. Blank values collapse to null: "" and "not stated" mean
     * the same thing to every caller here, and null is the one the form knows
     * how to leave alone. An unrendered template placeholder collapses to null
     * for the same reason.
     */
    static JsonNode truncateFields(JsonNode node, int maxLength) {
        if (!(node instanceof ObjectNode object)) {
            return node;
        }
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            JsonNode value = object.get(name);
            if (value == null || !value.isTextual()) {
                continue;
            }
            String truncated = truncate(value.asText(), maxLength);
            if (truncated == null || UNRENDERED_PLACEHOLDER.matcher(truncated).find()) {
                object.putNull(name);
            } else {
                object.put(name, truncated);
            }
        }
        return object;
    }

    static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * Nulls any value outside a field's allowed set.
     *
     * <p>A German posting invites "weiblich" where the schema says FEMALE. The
     * grammar should make that impossible, so this is a second line rather than
     * the first - but Jackson's failure mode is what makes it worth having:
     * mapping the record rejects the <em>whole object</em> over one bad value,
     * discarding the four fields the model read correctly. Null is what the
     * schema offered for "not stated" anyway, and the form leaves a null alone.
     */
    static JsonNode coerceEnums(JsonNode node, Map<String, Object> properties) {
        if (!(node instanceof ObjectNode object)) {
            return node;
        }
        properties.forEach((field, definition) -> allowedValues(definition).ifPresent(allowed -> {
            JsonNode value = object.get(field);
            if (value != null && value.isTextual() && !allowed.contains(value.asText())) {
                object.putNull(field);
            }
        }));
        return object;
    }

    /** The {@code enum} member of a schema fragment, minus the null the schema adds. */
    private static Optional<List<String>> allowedValues(Object definition) {
        if (!(definition instanceof Map<?, ?> fragment) || !(fragment.get("enum") instanceof List<?> values)) {
            return Optional.empty();
        }
        return Optional.of(values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList());
    }

    private JobPostingLlmPrompt() {
    }
}
