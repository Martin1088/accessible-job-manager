package de.samply.manager.jobimport.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builders for the JSON-schema fragments both providers send as their
 * structured-output contract.
 *
 * <p>Every field is nullable on purpose: the model needs a way to say "the
 * posting does not state this". Without it the only way to satisfy a required
 * string is to invent one, and a plausible invented street address is worse
 * than an empty field the user fills in themselves.
 */
public final class LlmSchema {

    public static Map<String, Object> nullableString() {
        return Map.of("type", List.of("string", "null"));
    }

    /**
     * A closed value set. The null member matters as much as the values: it is
     * what the model picks when the posting says nothing, rather than guessing
     * one of the real options.
     */
    public static Map<String, Object> nullableEnum(String... values) {
        List<String> allowed = new ArrayList<>(List.of(values));
        allowed.add(null);
        return Map.of("type", List.of("string", "null"), "enum", allowed);
    }

    private LlmSchema() {
    }
}
