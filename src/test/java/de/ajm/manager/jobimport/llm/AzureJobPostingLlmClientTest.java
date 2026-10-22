package de.ajm.manager.jobimport.llm;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the client against a real local HTTP server, since it builds its own {@code RestClient}. */
class AzureJobPostingLlmClientTest {

    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AzureJobPostingLlmClient clientAnswering(String body) {
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        return new AzureJobPostingLlmClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "gpt-4o", "2024-08-01-preview", "key", 2, 5,
                new ObjectMapper(), CoverLetterFixtures.messageSource());
    }

    @Test
    void anEmptyChoicesArrayFailsInsteadOfReturningAnAllNullResult() {
        AzureJobPostingLlmClient client = clientAnswering("{\"choices\":[]}");

        assertThatThrownBy(() -> client.extract("posting text"))
                .isInstanceOf(ApiException.BadGateway.class);
    }

    @Test
    void blankContentFailsInsteadOfReturningAnAllNullResult() {
        AzureJobPostingLlmClient client = clientAnswering(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}]}");

        assertThatThrownBy(() -> client.extract("posting text"))
                .isInstanceOf(ApiException.BadGateway.class);
    }
}
