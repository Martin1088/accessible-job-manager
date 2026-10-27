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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static de.ajm.manager.jobimport.llm.JobPostingLlmPrompt.truncateFields;

/** Calls a local Ollama instance's /api/chat in structured-output mode. */
@Service
@ConditionalOnProperty(name = "job-posting.parser.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaJobPostingLlmClient implements JobPostingLlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final String ollamaUrl;
    private final String ollamaModel;
    private final int readTimeoutSeconds;
    private final int maxOutputTokens;
    private final int contextWindow;

    /**
     * @param contextWindow Ollama's {@code num_ctx}; 0 leaves the model's own default,
     *                      which is what the posting parser has always run with. A CV
     *                      held against a posting does not fit that default, and Ollama
     *                      cuts the input off silently rather than refusing it.
     */
    public OllamaJobPostingLlmClient(@Value("${job-posting.parser.ollama-url}") String ollamaUrl,
                                      @Value("${job-posting.parser.model}") String ollamaModel,
                                      @Value("${job-posting.parser.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                      @Value("${job-posting.parser.read-timeout-seconds:120}") int readTimeoutSeconds,
                                      @Value("${job-posting.parser.max-output-tokens:300}") int maxOutputTokens,
                                      @Value("${job-posting.parser.context-window:0}") int contextWindow,
                                      ObjectMapper objectMapper,
                                      MessageSource messageSource) {
        this.ollamaUrl = ollamaUrl;
        this.ollamaModel = ollamaModel;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.contextWindow = contextWindow;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * Asks for the schema, and bounds how long the answer may run.
     *
     * <p>{@code num_predict} is not a tuning knob, it is what stops a hang. A
     * JSON string has no natural end, so a grammar cannot let the model emit EOS
     * in the middle of one - and a model unsure of a value will pad it rather
     * than close it. One posting produced
     * {@code "employmentType": "unbefristet (unendlich unbefristet? maybe
     * unbefristet is correct?) or..."} and never stopped: the same request timed
     * out at 420s uncapped and answered in 14.1s with the cap. It is the
     * rambling that costs, not the schema - the same schema on a longer input of
     * clean text finishes in 20s.
     *
     * <p>Asking for plain {@code format: "json"} instead was tried and reverted.
     * It was no faster on comparable input and it lost fields the schema kept:
     * on the application-method spec, same posting, the schema returned the
     * application address and JSON mode returned null for it.
     *
     * <p>The cap is a backstop, not the primary defence - a runaway value means
     * the input is bad, usually text an overlay corrupted before it got here
     * (see {@code PostingPdfTextExtractor}). It bounds the damage; it does not
     * make the answer good.
     */
    @Override
    public <T> T extract(String postingText, LlmExtractionSpec<T> spec) {
        Map<String, Object> format = Map.of(
                "type", "object",
                "properties", spec.properties(),
                "required", spec.requiredFields()
        );

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0);
        options.put("num_predict", maxOutputTokens);
        if (contextWindow > 0) {
            options.put("num_ctx", contextWindow);
        }

        Map<String, Object> body = Map.of(
                "model", ollamaModel,
                "stream", false,
                "options", options,
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
            throw LlmFailures.translate(e, messageSource, readTimeoutSeconds);
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            // num_predict reached: the answer stops mid-JSON and would only ever fail
            // as "unparsable", which names neither the cause nor the setting to change.
            if ("length".equals(root.path("done_reason").asText())) {
                throw LlmFailures.truncated(messageSource, root.path("eval_count").asInt(maxOutputTokens));
            }
            String content = root.path("message").path("content").asText();
            JsonNode fields = truncateFields(objectMapper.readTree(content), spec.maxFieldLength());
            // Belt and braces. The grammar should make an out-of-vocabulary enum
            // impossible, but Jackson's failure mode if one ever arrives is to
            // reject the whole object rather than the one field - losing four
            // good values to one bad one. Cheap enough to keep.
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
