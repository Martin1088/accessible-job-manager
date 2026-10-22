package de.ajm.manager.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GotenbergClientConfigTest {

    private HttpServer server;
    private final AtomicReference<String> token = new AtomicReference<>("untouched");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Auth-Token"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void call(String authToken) {
        RestClient client = new GotenbergClientConfig().gotenbergRestClient(2, 5, authToken);
        client.get().uri("http://127.0.0.1:" + server.getAddress().getPort() + "/health")
                .retrieve().toBodilessEntity();
    }

    @Test
    void sendsTheTokenAsXAuthToken() {
        call("iam-secret");

        assertThat(token.get()).isEqualTo("iam-secret");
    }

    @Test
    void sendsNoHeaderWithoutAToken() {
        call("");

        assertThat(token.get()).isNull();
    }
}
