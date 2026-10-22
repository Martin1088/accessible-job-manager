package de.ajm.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import de.ajm.manager.coverletter.CoverLetterFixtures;
import de.ajm.manager.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The token limits against a fake Ollama: {@code num_ctx} is sent only when a context
 * window is configured - the posting parser never set one and keeps the model's
 * default, the CV matching needs a larger one - and an answer that ran into
 * {@code num_predict} is reported as cut off.
 */
class OllamaContextWindowTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> sent = new AtomicReference<>();
    private final AtomicReference<String> answer = new AtomicReference<>("""
            {"message":{"content":"{\\"title\\":null,\\"company\\":null,\\"location\\":null,\\"employmentType\\":null}"},
             "done_reason":"stop"}""");
    private HttpServer server;

    @BeforeEach
    void fakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            sent.set(mapper.readTree(exchange.getRequestBody()));
            byte[] body = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OllamaJobPostingLlmClient client(int contextWindow) {
        return new OllamaJobPostingLlmClient("http://127.0.0.1:" + server.getAddress().getPort(), "qwen2.5:3b",
                5, 10, 300, contextWindow, mapper, CoverLetterFixtures.messageSource());
    }

    private JsonNode optionsSentWith(int contextWindow) {
        client(contextWindow).extract("A posting.");
        return sent.get().path("options");
    }

    @Test
    void leavesTheModelsDefaultWhenNoneIsConfigured() {
        JsonNode options = optionsSentWith(0);
        assertThat(options.has("num_ctx")).isFalse();
        assertThat(options.path("num_predict").asInt()).isEqualTo(300);
    }

    @Test
    void sendsAConfiguredContextWindow() {
        assertThat(optionsSentWith(16384).path("num_ctx").asInt()).isEqualTo(16384);
    }

    /**
     * An answer stopped at num_predict ends mid-JSON. It used to fail as "could not
     * parse", which pointed at the model's output rather than at the limit.
     */
    @Test
    void namesAnAnswerCutOffAtTheOutputLimit() {
        answer.set("""
                {"message":{"content":"{\\"title\\":\\"Frontend Eng"},"done_reason":"length","eval_count":300}""");

        assertThatThrownBy(() -> client(0).extract("A posting."))
                .isInstanceOf(ApiException.BadGateway.class)
                .hasMessageContaining("cut off at 300 tokens")
                .hasMessageContaining("max-output-tokens");
    }
}
