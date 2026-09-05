package de.samply.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static de.samply.manager.jobimport.llm.JobPostingLlmPrompt.truncateFields;

/** Calls an Azure OpenAI chat completions deployment in structured-output (json_schema) mode. */
@Service
@ConditionalOnProperty(name = "job-posting.parser.provider", havingValue = "azure")
public class AzureJobPostingLlmClient implements JobPostingLlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final String endpoint;
    private final String deployment;
    private final String apiVersion;
    private final String apiKey;

    public AzureJobPostingLlmClient(@Value("${job-posting.parser.azure.endpoint}") String endpoint,
                                     @Value("${job-posting.parser.azure.deployment}") String deployment,
                                     @Value("${job-posting.parser.azure.api-version}") String apiVersion,
                                     @Value("${job-posting.parser.azure.api-key}") String apiKey,
                                     ObjectMapper objectMapper,
                                     MessageSource messageSource) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.deployment = deployment;
        this.apiVersion = apiVersion;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public <T> T extract(String postingText, LlmExtractionSpec<T> spec) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", spec.properties(),
                "required", spec.requiredFields()
        );

        Map<String, Object> body = Map.of(
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", spec.systemPrompt()),
                        Map.of("role", "user", "content", postingText)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", spec.name(),
                                "strict", true,
                                "schema", schema
                        )
                )
        );

        String uri = "%s/openai/deployments/%s/chat/completions?api-version=%s"
                .formatted(endpoint, deployment, apiVersion);

        String response;
        try {
            response = restClient.post()
                    .uri(uri)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ApiException.BadGateway(message("error.llm.unavailable"));
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            // path(), not get(): an empty/errored choices array (a content-filtered
            // response, a quota error with a 200 envelope) must fail as "unparsable",
            // not throw a NullPointerException that skips past this catch block.
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode fields = truncateFields(objectMapper.readTree(content), spec.maxFieldLength());
            return objectMapper.treeToValue(fields, spec.type());
        } catch (IOException e) {
            throw new ApiException.BadGateway(message("error.llm.unparsable"));
        }
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, Locale.ROOT);
    }
}
