package de.samply.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One bad enum value must not cost the fields the model got right.
 *
 * <p>The schema Ollama is given should already make an out-of-vocabulary value
 * impossible, so this guards the failure mode rather than the common case: when
 * one does arrive, Jackson rejects the entire object while mapping the record,
 * turning one misread field into a lost extraction.
 */
class EnumCoercionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aValueOutsideTheAllowedSetBecomesNullInsteadOfLosingTheObject() throws Exception {
        // "weiblich" is exactly what a German posting invites a model to answer
        // once nothing constrains it to the enum.
        JsonNode answered = mapper.readTree("""
                {"title":"Teamleitung","contactGender":"weiblich","contactLastName":"Sodoli"}
                """);

        JsonNode coerced = JobPostingLlmPrompt.coerceEnums(answered, JobPostingLlmSpecs.POSITION.properties());

        assertThat(coerced.get("contactGender").isNull()).isTrue();
        // The point of coercing rather than rejecting: the fields it did read stay.
        assertThat(coerced.get("contactLastName").asText()).isEqualTo("Sodoli");
        assertThat(coerced.get("title").asText()).isEqualTo("Teamleitung");
    }

    @Test
    void anAllowedValueSurvivesUntouched() throws Exception {
        JsonNode answered = mapper.readTree("{\"contactGender\":\"FEMALE\"}");

        JsonNode coerced = JobPostingLlmPrompt.coerceEnums(answered, JobPostingLlmSpecs.POSITION.properties());

        assertThat(coerced.get("contactGender").asText()).isEqualTo("FEMALE");
    }

    @Test
    void aFieldWithNoAllowedSetIsLeftAlone() throws Exception {
        JsonNode answered = mapper.readTree("{\"title\":\"anything at all\"}");

        JsonNode coerced = JobPostingLlmPrompt.coerceEnums(answered, JobPostingLlmSpecs.OVERVIEW.properties());

        assertThat(coerced.get("title").asText()).isEqualTo("anything at all");
    }

    @Test
    void coercionToleratesANonObjectAnswer() throws Exception {
        JsonNode array = mapper.readTree("[1,2,3]");

        assertThat(JobPostingLlmPrompt.coerceEnums(array, Map.of())).isSameAs(array);
    }
}
