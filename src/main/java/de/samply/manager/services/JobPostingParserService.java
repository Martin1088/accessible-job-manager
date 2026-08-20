package de.samply.manager.services;

import de.samply.manager.dto.JobPostingExtraction;
import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;

/**
 * Fetches a job posting from a user-supplied URL (with SSRF-safe host
 * validation and redirect handling) and hands the visible text to a
 * JobPostingLlmClient to extract the key fields. Which LLM provider is used
 * (Ollama, Azure OpenAI, ...) is decided by job-posting.parser.provider -
 * see de.samply.manager.jobimport.llm.
 */
@Service
public class JobPostingParserService {

    private static final int MAX_HTML_BYTES = 3_000_000;
    private static final int MAX_TEXT_CHARS = 8_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_LINKS = 60;

    private final JobPostingLlmClient llmClient;
    private final HttpClient httpClient;

    public JobPostingParserService(JobPostingLlmClient llmClient) {
        this.llmClient = llmClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Resolved URL (post-redirects) plus the raw HTML fetched from it. */
    public record FetchedPage(URI url, String html) {}

    public JobPostingExtraction overview(String rawUrl) {
        URI uri = validate(rawUrl);
        String text = visibleText(fetchHtml(uri));
        return llmClient.extract(text);
    }

    /**
     * Same fetch/redirect/SSRF-validation path as {@link #overview}, but
     * returns the raw HTML instead of extracted+truncated text - for callers
     * that need the parsed DOM themselves (e.g. reading JSON-LD script tags).
     */
    public FetchedPage fetchPage(String rawUrl) {
        return fetchHtml(validate(rawUrl));
    }

    /** The posting's visible text, fetched through the same validated path. */
    public String postingText(String rawUrl) {
        return visibleText(fetchHtml(validate(rawUrl)));
    }

    /**
     * The posting's visible text followed by the links found on the page.
     *
     * <p>For deciding <em>how</em> to apply, the text alone is not enough: an
     * apply button carries its destination in an href, and a mailto: address
     * may never appear as text at all. Without the hrefs a model asked for an
     * application URL can only invent one.
     *
     * <p>The links are listed unfiltered (beyond de-duplication and a size
     * cap) rather than pre-selected by an apply-looking heuristic, so the
     * choice of which link is the application link stays with the model.
     */
    public String postingTextWithLinks(String rawUrl) {
        FetchedPage page = fetchHtml(validate(rawUrl));
        Document document = Jsoup.parse(page.html(), page.url().toString());
        String text = truncateText(document.text());

        LinkedHashSet<String> links = new LinkedHashSet<>();
        for (Element anchor : document.select("a[href]")) {
            if (links.size() >= MAX_LINKS) break;
            String href = anchor.absUrl("href");
            if (href.isBlank()) href = anchor.attr("href");
            if (href.isBlank() || href.startsWith("javascript:")) continue;
            String label = anchor.text().strip();
            links.add(label.isBlank() ? href : label + " -> " + href);
        }
        if (links.isEmpty()) {
            return text;
        }
        return text + "\n\nLinks on the page:\n" + String.join("\n", links);
    }

    private URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must not be empty");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed URL");
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must include a host");
        }

        rejectIfDisallowedHost(uri.getHost());
        return uri;
    }

    private void rejectIfDisallowedHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve host");
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "URL points to a disallowed network address");
            }
        }
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private FetchedPage fetchHtml(URI uri) {
        URI target = uri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "accessible-job-manager/1.0 (+job posting import)")
                    .header("Accept", "text/html")
                    .GET()
                    .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the given URL");
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Redirect without Location header"));
                target = validate(target.resolve(location).toString());
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "URL responded with status " + status);
            }

            byte[] html = readBounded(response.body());
            return new FetchedPage(target, new String(html, StandardCharsets.UTF_8));
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Too many redirects");
    }

    private String visibleText(FetchedPage page) {
        return truncateText(Jsoup.parse(page.html(), page.url().toString()).text());
    }

    private String truncateText(String text) {
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    private byte[] readBounded(InputStream in) {
        try (in) {
            return in.readNBytes(MAX_HTML_BYTES);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read response from the given URL");
        }
    }

}
