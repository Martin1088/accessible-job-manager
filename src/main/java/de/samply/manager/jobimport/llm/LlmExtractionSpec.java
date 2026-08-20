package de.samply.manager.jobimport.llm;

import java.util.List;
import java.util.Map;

/**
 * One structured-output contract: the prompt telling the model what to look
 * for, the JSON schema it must answer in, and the type that answer maps to.
 *
 * <p>Each provider translates this into its own structured-output dialect -
 * Ollama's {@code format} block, Azure OpenAI's {@code json_schema} response
 * format - so a new kind of extraction is a new spec here rather than an edit
 * in every client.
 *
 * @param maxFieldLength cap applied to each returned string, see
 *                       {@link JobPostingLlmPrompt#MAX_FIELD_LENGTH}
 */
public record LlmExtractionSpec<T>(String name, String systemPrompt,
                                   Map<String, Object> properties, Class<T> type,
                                   int maxFieldLength) {

    public LlmExtractionSpec(String name, String systemPrompt,
                             Map<String, Object> properties, Class<T> type) {
        this(name, systemPrompt, properties, type, JobPostingLlmPrompt.MAX_FIELD_LENGTH);
    }

    /**
     * Every field is required, so the model always answers with the complete
     * object; "absent" is expressed as an explicit null, which the schema
     * allows. Leaving fields optional instead invites a model to omit the ones
     * it is least sure about - exactly the ones worth seeing as null.
     */
    public List<String> requiredFields() {
        return List.copyOf(properties.keySet());
    }
}
