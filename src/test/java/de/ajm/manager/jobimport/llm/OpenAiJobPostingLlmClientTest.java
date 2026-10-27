package de.ajm.manager.jobimport.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import de.ajm.manager.coverletter.CoverLetterFixtures;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.jobimport.JobPostingExtraction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the client against a real local HTTP server, since it builds its own {@code RestClient}. */
class OpenAiJobPostingLlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(int status, String body) {
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private OpenAiJobPostingLlmClient client(String pathPrefix) {
        // A trailing slash on the base URL is what people paste; it must not double up.
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + pathPrefix + "/";
        return new OpenAiJobPostingLlmClient(baseUrl, "mistral-small-3.2-24b-instruct-2506", "secret-key",
                2, 5, 300, MAPPER, CoverLetterFixtures.messageSource());
    }

    private static String completion(String finishReason, String content) throws IOException {
        return MAPPER.writeValueAsString(MAPPER.createObjectNode()
                .set("choices", MAPPER.createArrayNode().add(MAPPER.createObjectNode()
                        .put("finish_reason", finishReason)
                        .set("message", MAPPER.createObjectNode().put("content", content)))));
    }

    @Test
    void postsToChatCompletionsWithABearerKeyAndTheSchema() throws IOException {
        respond(200, completion("stop",
                "{\"title\":\"Developer\",\"company\":\"ACME\",\"location\":null,\"employmentType\":null}"));

        JobPostingExtraction extraction = client("/v1").extract("posting text");

        assertThat(extraction.title()).isEqualTo("Developer");
        assertThat(extraction.company()).isEqualTo("ACME");
        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(authorization.get()).isEqualTo("Bearer secret-key");

        JsonNode body = MAPPER.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("mistral-small-3.2-24b-instruct-2506");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(300);
        assertThat(body.path("temperature").asInt()).isZero();
        assertThat(body.path("messages").path(1).path("content").asText()).isEqualTo("posting text");
        JsonNode format = body.path("response_format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("schema").path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(format.path("json_schema").path("schema").path("required")).hasSize(4);
    }

    @Test
    void aMaxTokensStopIsReportedAsTruncatedNotAsUnparsable() throws IOException {
        respond(200, completion("length", "{\"title\":\"Devel"));

        assertThatThrownBy(() -> client("/v1").extract("posting text"))
                .isInstanceOf(ApiException.BadGateway.class)
                .hasMessageNotContaining("unparsable");
    }

    @Test
    void anEmptyChoicesArrayFailsAsAnApiErrorNotANullPointer() {
        respond(200, "{\"choices\":[]}");

        assertThatThrownBy(() -> client("/v1").extract("posting text"))
                .isInstanceOf(ApiException.BadGateway.class);
    }

    @Test
    void anUpstreamErrorNamesTheStatus() {
        respond(401, "{\"message\":\"invalid key\"}");

        assertThatThrownBy(() -> client("/v1").extract("posting text"))
                .isInstanceOf(ApiException.BadGateway.class)
                .hasMessageContaining("401");
    }
}
