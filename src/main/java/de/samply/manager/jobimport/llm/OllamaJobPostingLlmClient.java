package de.samply.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.dto.JobPostingExtraction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static de.samply.manager.jobimport.llm.JobPostingLlmPrompt.SYSTEM_PROMPT;
import static de.samply.manager.jobimport.llm.JobPostingLlmPrompt.truncate;

/** Calls a local Ollama instance's /api/chat in structured-output mode. */
@Service
@ConditionalOnProperty(name = "job-posting.parser.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaJobPostingLlmClient implements JobPostingLlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String ollamaUrl;
    private final String ollamaModel;

    public OllamaJobPostingLlmClient(@Value("${job-posting.parser.ollama-url}") String ollamaUrl,
                                      @Value("${job-posting.parser.model}") String ollamaModel,
                                      ObjectMapper objectMapper) {
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public JobPostingExtraction extract(String postingText) {
        Map<String, Object> format = Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", List.of("string", "null")),
                        "company", Map.of("type", List.of("string", "null")),
                        "location", Map.of("type", List.of("string", "null")),
                        "employmentType", Map.of("type", List.of("string", "null"))
                ),
                "required", List.of("title", "company", "location", "employmentType")
        );

        Map<String, Object> body = Map.of(
                "model", ollamaModel,
                "stream", false,
                "options", Map.of("temperature", 0),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", postingText)
                ),
                "format", format
        );

        String response;
        try {
            response = restClient.post()
                    .uri(ollamaUrl + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Job posting extraction service unavailable");
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("message").path("content").asText();
            JobPostingExtraction raw = objectMapper.readValue(content, JobPostingExtraction.class);
            return new JobPostingExtraction(
                    truncate(raw.title()), truncate(raw.company()),
                    truncate(raw.location()), truncate(raw.employmentType()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse extraction result");
        }
    }
}
