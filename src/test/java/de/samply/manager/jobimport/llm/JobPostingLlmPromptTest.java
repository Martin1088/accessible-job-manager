package de.samply.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingLlmPromptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode guard(String json) throws Exception {
        return JobPostingLlmPrompt.truncateFields(mapper.readTree(json), JobPostingLlmPrompt.MAX_FIELD_LENGTH);
    }

    @Test
    void capsAnOverlongField() throws Exception {
        JsonNode out = guard("{\"title\":\"" + "x".repeat(500) + "\"}");
        assertThat(out.get("title").asText()).hasSize(JobPostingLlmPrompt.MAX_FIELD_LENGTH);
    }

    @Test
    void blankBecomesNull() throws Exception {
        assertThat(guard("{\"company\":\"   \"}").get("company").isNull()).isTrue();
    }

    /**
     * The Comeet regression: a client-rendered shell's only text is its own
     * template, and the model copies the placeholder into the field. That is a
     * "not found", not a value.
     */
    @Test
    void anUnrenderedTemplatePlaceholderBecomesNull() throws Exception {
        JsonNode out = guard("""
                { "title": "{{position.name}}",
                  "company": "{{ company.name }}",
                  "location": "${job.location}",
                  "employmentType": "Full-time" }
                """);

        assertThat(out.get("title").isNull()).isTrue();
        assertThat(out.get("company").isNull()).isTrue();
        assertThat(out.get("location").isNull()).isTrue();
        assertThat(out.get("employmentType").asText()).isEqualTo("Full-time");
    }

    @Test
    void leavesAPlainValueAlone() throws Exception {
        assertThat(guard("{\"title\":\"Backend Engineer (m/w/d)\"}").get("title").asText())
                .isEqualTo("Backend Engineer (m/w/d)");
    }
}
