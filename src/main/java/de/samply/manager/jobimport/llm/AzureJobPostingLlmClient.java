package de.samply.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.dto.JobPostingExtraction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static de.samply.manager.jobimport.llm.JobPostingLlmPrompt.SYSTEM_PROMPT;
import static de.samply.manager.jobimport.llm.JobPostingLlmPrompt.truncate;

/** Calls an Azure OpenAI chat completions deployment in structured-output (json_schema) mode. */
@Service
@ConditionalOnProperty(name = "job-posting.parser.provider", havingValue = "azure")
public class AzureJobPostingLlmClient implements JobPostingLlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String deployment;
    private final String apiVersion;
    private final String apiKey;

    public AzureJobPostingLlmClient(@Value("${job-posting.parser.azure.endpoint}") String endpoint,
                                     @Value("${job-posting.parser.azure.deployment}") String deployment,
                                     @Value("${job-posting.parser.azure.api-version}") String apiVersion,
                                     @Value("${job-posting.parser.azure.api-key}") String apiKey,
                                     ObjectMapper objectMapper) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.deployment = deployment;
        this.apiVersion = apiVersion;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public JobPostingExtraction extract(String postingText) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "title", Map.of("type", List.of("string", "null")),
                        "company", Map.of("type", List.of("string", "null")),
                        "location", Map.of("type", List.of("string", "null")),
                        "employmentType", Map.of("type", List.of("string", "null"))
                ),
                "required", List.of("title", "company", "location", "employmentType")
        );

        Map<String, Object> body = Map.of(
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", postingText)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "job_posting_extraction",
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Job posting extraction service unavailable");
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JobPostingExtraction raw = objectMapper.readValue(content, JobPostingExtraction.class);
            return new JobPostingExtraction(
                    truncate(raw.title()), truncate(raw.company()),
                    truncate(raw.location()), truncate(raw.employmentType()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse extraction result");
        }
    }
}
