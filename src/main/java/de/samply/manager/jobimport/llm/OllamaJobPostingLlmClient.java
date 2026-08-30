package de.samply.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static de.samply.manager.jobimport.llm.JobPostingLlmPrompt.truncateFields;

/** Calls a local Ollama instance's /api/chat in structured-output mode. */
@Service
@ConditionalOnProperty(name = "job-posting.parser.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaJobPostingLlmClient implements JobPostingLlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final String ollamaUrl;
    private final String ollamaModel;

    public OllamaJobPostingLlmClient(@Value("${job-posting.parser.ollama-url}") String ollamaUrl,
                                      @Value("${job-posting.parser.model}") String ollamaModel,
                                      ObjectMapper objectMapper,
                                      MessageSource messageSource) {
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public <T> T extract(String postingText, LlmExtractionSpec<T> spec) {
        Map<String, Object> format = Map.of(
                "type", "object",
                "properties", spec.properties(),
                "required", spec.requiredFields()
        );

        Map<String, Object> body = Map.of(
                "model", ollamaModel,
                "stream", false,
                "options", Map.of("temperature", 0),
                "messages", List.of(
                        Map.of("role", "system", "content", spec.systemPrompt()),
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
            throw new ApiException.BadGateway(message("error.llm.unavailable"));
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("message").path("content").asText();
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
