package de.ajm.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The one HTTP client every Gotenberg call goes through.
 *
 * <p>The three call sites each built their own {@code RestClient.create()},
 * which takes the JDK default request factory and therefore has no connect or
 * read timeout at all - a Gotenberg that accepts the connection and then never
 * answers held the calling request open indefinitely. That the omission was an
 * oversight rather than a decision is visible one package over:
 * {@code OllamaJobPostingLlmClient} builds its factory by hand precisely so it
 * can set 5s/120s, in the same idiom used here.
 *
 * <p>The read timeout is deliberately <em>longer</em> than Gotenberg's own
 * {@code --api-timeout} (30s, set explicitly in the compose files). Gotenberg
 * answers a render that runs over its own limit with a real HTTP error naming
 * the page that hung; a client that gave up first would replace that with a
 * transport exception and lose the reason. This timeout is the backstop for a
 * Gotenberg that stopped answering at all, not the render budget.
 *
 * <p>A Gotenberg that is not reachable from the open network (a private Scaleway
 * container) wants a token in {@code X-Auth-Token}. It is set once here, as a default
 * header, so the three call sites cannot disagree about it; unset, nothing is sent, which
 * is what the compose files' unauthenticated Gotenberg expects.
 */
@Configuration
public class GotenbergClientConfig {

    /** The header Scaleway's IAM-protected containers read the caller's key from. */
    static final String AUTH_HEADER = "X-Auth-Token";

    @Bean
    public RestClient gotenbergRestClient(
            @Value("${gotenberg.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${gotenberg.read-timeout-seconds:60}") int readTimeoutSeconds,
            @Value("${gotenberg.auth-token:}") String authToken) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (!authToken.isBlank()) {
            builder.defaultHeader(AUTH_HEADER, authToken);
        }
        return builder.build();
    }
}
