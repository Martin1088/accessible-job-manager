package de.samply.manager.jobimport.render;

import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.diagnostics.FailureCategory;
import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.security.OutboundUrlGuard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prints a job posting URL through Gotenberg's Chromium.
 *
 * <p>The one place that talks to {@code /forms/chromium/convert/url}. It was
 * previously inside {@code JobPostingSnapshotService}, which also owns S3
 * storage and the {@code Document} rows - fine while a render was only ever an
 * archive, but the extraction path needs the same render for text and has no
 * business depending on a storage service to get it.
 *
 * <p>What the URL guard buys here is worth being precise about, because it is
 * less than it looks: Gotenberg fetches the page itself, from its own
 * container, following its own redirects, so validating the address here stops
 * the obvious internal URL and nothing after the first hop. The containment
 * that actually holds is the isolated network plus
 * {@code --chromium-deny-private-ips}, both described in Readme.md.
 */
@Component
public class PostingRenderer {

    private static final Pattern UPSTREAM_STATUS_PATTERN =
            Pattern.compile("status code[^0-9]*(\\d{3})", Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final String gotenbergUrl;
    private final String waitDelay;
    private final MessageSource messageSource;
    private final ImportDiagnostics diagnostics;
    private final OutboundUrlGuard urlGuard;
    private final RenderCache cache;

    public PostingRenderer(RestClient gotenbergRestClient,
                           @Value("${gotenberg.url}") String gotenbergUrl,
                           @Value("${gotenberg.wait-delay:1s}") String waitDelay,
                           MessageSource messageSource,
                           ImportDiagnostics diagnostics,
                           OutboundUrlGuard urlGuard,
                           RenderCache cache) {
        this.restClient = gotenbergRestClient;
        this.gotenbergUrl = gotenbergUrl;
        this.waitDelay = waitDelay;
        this.messageSource = messageSource;
        this.diagnostics = diagnostics;
        this.urlGuard = urlGuard;
        this.cache = cache;
    }

    /**
     * The posting at {@code rawUrl} as a PDF, rendered for {@code profile} or
     * taken from the cache if this import already rendered it that way.
     *
     * @throws ApiException.BadRequest   the URL is not one we will fetch, or the
     *                                   page itself failed to load
     * @throws ApiException.BadGateway   Gotenberg could not be reached
     */
    public byte[] render(String rawUrl, String userId, RenderProfile profile) {
        URI uri = urlGuard.validate(rawUrl);
        String url = uri.toString();

        byte[] cached = cache.get(userId, url, profile);
        if (cached != null) {
            return cached;
        }

        try {
            byte[] pdf = restClient.post()
                    .uri(gotenbergUrl + "/forms/chromium/convert/url")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(profile.body(url, waitDelay))
                    .retrieve()
                    .body(byte[].class);
            diagnostics.record(url, FailureCategory.OK, null);
            cache.put(userId, url, profile, pdf);
            return pdf;
        } catch (RestClientResponseException e) {
            ApiException failure = conversionFailure(e);
            diagnostics.record(url, FailureCategory.PDF_FETCH_FAILED, failure.getUpstreamStatus());
            throw failure;
        } catch (RestClientException e) {
            diagnostics.record(url, FailureCategory.PDF_SERVICE_UNAVAILABLE, null);
            throw new ApiException.BadGateway(message("error.snapshot.serviceUnavailable"));
        }
    }

    /**
     * Gotenberg reports a failed page load as "...HTTP status code from the main page: {status}: ..."
     * in its error body (e.g. a job posting that was taken down surfaces as a 404 there).
     * Extracting that status lets us tell the user why their URL failed instead of a generic 502,
     * and carrying it on the exception lets the diagnostics line name the status without
     * re-parsing the localized message it ends up in.
     */
    private ApiException conversionFailure(RestClientResponseException e) {
        Matcher matcher = UPSTREAM_STATUS_PATTERN.matcher(e.getResponseBodyAsString());
        if (!matcher.find()) {
            return new ApiException.BadRequest(message("error.snapshot.conversionFailed"));
        }
        int upstreamStatus = Integer.parseInt(matcher.group(1));
        String description = switch (upstreamStatus) {
            case 404 -> message("error.snapshot.notFound404");
            case 401, 403 -> message("error.snapshot.deniedAccess", upstreamStatus);
            default -> message("error.snapshot.upstreamError", upstreamStatus);
        };
        return new ApiException.BadRequest(description, upstreamStatus);
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
