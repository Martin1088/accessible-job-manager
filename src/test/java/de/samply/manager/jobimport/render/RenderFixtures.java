package de.samply.manager.jobimport.render;

import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.security.OutboundUrlGuard;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.client.RestClient;

/**
 * A {@link PostingRenderer} that would rather fail the test than reach
 * Gotenberg.
 *
 * <p>Every URL the guard is supposed to refuse must be refused <em>before</em>
 * the render, and the only way to assert that rather than assume it is to make
 * the call itself an error. Shared because two suites need the same guarantee:
 * the renderer's own, and the parser service that now depends on it for URL
 * validation.
 */
public final class RenderFixtures {

    private RenderFixtures() {}

    public static MessageSource messages() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    /** A renderer wired to the real guard, whose HTTP client fails on use. */
    public static PostingRenderer refusingRenderer(ImportDiagnostics diagnostics) {
        MessageSource messages = messages();
        RestClient neverCalled = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new AssertionError("Gotenberg called for a URL that should have been refused: " + uri);
                })
                .build();

        return new PostingRenderer(neverCalled, "http://gotenberg", "1s", messages, diagnostics,
                new OutboundUrlGuard(messages, 30), new RenderCache(120, 32, 134_217_728L));
    }
}
