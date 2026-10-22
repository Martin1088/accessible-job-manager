package de.ajm.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ajm.manager.exception.ApiException;
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

import static de.ajm.manager.jobimport.llm.JobPostingLlmPrompt.truncateFields;

/**
 * Calls any OpenAI-compatible {@code /chat/completions} endpoint in structured-output
 * (json_schema) mode - Scaleway Generative APIs, but equally a vLLM or a LiteLLM in front
 * of something else. The deployment is named by a base URL and a model, not by a vendor.
 *
 * <p>Not the Azure client with another URL: Azure addresses a <em>deployment</em> in the
 * path, authenticates with an {@code api-key} header and needs an {@code api-version}.
 * Here the model travels in the body and the key as a bearer token.
 */
@Service
@ConditionalOnProperty(name = "job-posting.parser.provider", havingValue = "openai")
public class OpenAiJobPostingLlmClient implements JobPostingLlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int readTimeoutSeconds;
    private final int maxOutputTokens;

    public OpenAiJobPostingLlmClient(@Value("${job-posting.parser.openai.base-url}") String baseUrl,
                                     @Value("${job-posting.parser.openai.model}") String model,
                                     @Value("${job-posting.parser.openai.api-key}") String apiKey,
                                     @Value("${job-posting.parser.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                     @Value("${job-posting.parser.read-timeout-seconds:120}") int readTimeoutSeconds,
                                     @Value("${job-posting.parser.max-output-tokens:300}") int maxOutputTokens,
                                     ObjectMapper objectMapper,
                                     MessageSource messageSource) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.apiKey = apiKey;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;

        // Timeouts for the reason GotenbergClientConfig gives: a bare RestClient has none.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public <T> T extract(String postingText, LlmExtractionSpec<T> spec) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", spec.properties(),
                "required", spec.requiredFields()
        );

        // max_tokens is the backstop the Ollama client documents as num_predict: a model
        // unsure of a value pads it rather than closing the string.
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0,
                "max_tokens", maxOutputTokens,
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

        String response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw LlmFailures.translate(e, messageSource, readTimeoutSeconds);
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            if ("length".equals(root.path("choices").path(0).path("finish_reason").asText())) {
                throw LlmFailures.truncated(messageSource, root.path("usage").path("completion_tokens").asInt(maxOutputTokens));
            }
            // path(), not get(): an empty choices array must fail as "unparsable", not as
            // a NullPointerException that skips past this catch block.
            String content = root.path("choices").path(0).path("message").path("content").asText();
            // Blank content parses to a missing node that maps to an all-null object, which
            // would pass for "the posting states nothing" - it has to fail instead.
            if (content.isBlank()) {
                throw new ApiException.BadGateway(message("error.llm.unparsable"));
            }
            JsonNode fields = truncateFields(objectMapper.readTree(content), spec.maxFieldLength());
            // Not every OpenAI-compatible server compiles the schema into a grammar, so an
            // out-of-vocabulary enum value can arrive; see the Ollama client.
            fields = JobPostingLlmPrompt.coerceEnums(fields, spec.properties());
            return objectMapper.treeToValue(fields, spec.type());
        } catch (IOException e) {
            throw new ApiException.BadGateway(message("error.llm.unparsable"), e);
        }
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, Locale.ROOT);
    }
}
