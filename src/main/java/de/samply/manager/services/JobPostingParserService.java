package de.samply.manager.services;

import de.samply.manager.dto.JobPostingExtraction;
import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.PostingPdfTextExtractor;
import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import de.samply.manager.jobimport.render.PostingRenderer;
import de.samply.manager.jobimport.render.RenderProfile;
import de.samply.manager.security.OutboundUrlGuard;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Reads a job posting from a user-supplied URL and hands its visible text to a
 * JobPostingLlmClient to extract the key fields. Which LLM provider is used
 * (Ollama, Azure OpenAI, ...) is decided by job-posting.parser.provider -
 * see de.samply.manager.jobimport.llm.
 *
 * <p>{@link #overview} gets that text by printing the page through Chromium;
 * everything else here still fetches HTML directly, with SSRF-safe host
 * validation and its own redirect handling. Both paths are kept because they
 * answer different questions - see the note on {@code overview}.
 */
@Service
public class JobPostingParserService {

    private static final int MAX_HTML_BYTES = 3_000_000;
    private static final int MAX_TEXT_CHARS = 8_000;
    /** Below this, pasted text is a headline rather than a posting. */
    private static final int MIN_TEXT_CHARS = 120;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_LINKS = 60;

    private final JobPostingLlmClient llmClient;
    private final HttpClient httpClient;
    private final MessageSource messageSource;
    private final PostingPdfTextExtractor pdfTextExtractor;
    private final OutboundUrlGuard urlGuard;
    private final PostingRenderer renderer;

    public JobPostingParserService(JobPostingLlmClient llmClient,
                                   MessageSource messageSource,
                                   PostingPdfTextExtractor pdfTextExtractor,
                                   OutboundUrlGuard urlGuard,
                                   PostingRenderer renderer) {
        this.llmClient = llmClient;
        this.messageSource = messageSource;
        this.pdfTextExtractor = pdfTextExtractor;
        this.urlGuard = urlGuard;
        this.renderer = renderer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Resolved URL (post-redirects) plus the raw HTML fetched from it. */
    public record FetchedPage(URI url, String html) {}

    /**
     * The posting's key fields, read by the model from the page as a browser
     * renders it.
     *
     * <p>This used to be a plain GET whose HTML went to the model as
     * {@code Jsoup.text()}, which meant a posting whose body is written by
     * JavaScript arrived as an empty page - the case
     * {@code JobPostingImportService} has always classified as
     * {@code JS_REQUIRED} and {@code FailureCategory} annotated with "Fix:
     * fetch through Chromium". Gotenberg was already rendering the very same
     * URL a few seconds later for the archived snapshot and the result was
     * thrown away, so the fix was to read what was already being produced.
     *
     * <p>Nothing is given up by printing rather than fetching here: this path
     * never looked at the markup, only at the visible text, and Chromium's is
     * strictly better. That is not true of the full-chain extraction, which
     * lives or dies by JSON-LD and microdata that a PDF cannot carry - which is
     * why that one still fetches HTML and this one no longer does.
     *
     * <p>The plain GET remains as the fallback, so a static posting still
     * imports while Gotenberg is down.
     */
    public JobPostingExtraction overview(String rawUrl, String userId) {
        byte[] rendered;
        try {
            rendered = renderer.render(rawUrl, userId, RenderProfile.EXTRACTION);
        } catch (ApiException.BadGateway e) {
            // Gotenberg unreachable - our outage, not the posting's. A page that
            // needed a browser will fail on the GET too, but a plain one imports
            // fine, and most postings are plain.
            return llmClient.extract(visibleText(fetchHtml(urlGuard.validate(rawUrl))));
        }

        try {
            return overviewFromPdf(rendered);
        } catch (ApiException.BadRequest e) {
            // The page rendered but carried no readable posting - a consent wall
            // or a login screen printed instead of an ad. The length rules stay
            // shared with the upload path, but its wording cannot: telling
            // someone whose URL rendered blank that their file might be "a scan
            // or a screenshot" is advice about a file they never had.
            throw new ApiException.BadRequest(message("error.posting.renderedNoText"), e);
        }
    }

    /**
     * The same extraction as {@link #overview}, on text the user pasted rather
     * than text fetched from a URL.
     *
     * <p>This is the answer to a host we cannot read: aggregators answer 403 to
     * every request from a server, and a posting behind a login cannot be
     * fetched at all. Both are the same problem from here - no text - and a
     * person looking at the page always has the text. Only the fetch is skipped;
     * everything downstream is the path {@code overview} already takes, so the
     * two cannot drift apart.
     *
     * <p>There is no text-equivalent of the full-chain extraction: that one
     * follows links out of the page, which needs the page.
     */
    public JobPostingExtraction overviewFromText(String postingText) {
        if (postingText == null || postingText.isBlank()) {
            throw new ApiException.BadRequest(message("error.postingText.empty"));
        }
        String text = postingText.strip();
        // A handful of words is a title, not a posting. Extracting from it would
        // return a row of nulls that reads as a parser failure rather than as
        // too little input, so it is refused with a reason instead.
        if (text.length() < MIN_TEXT_CHARS) {
            throw new ApiException.BadRequest(message("error.postingText.tooShort", MIN_TEXT_CHARS));
        }
        return llmClient.extract(truncateText(text));
    }

    /**
     * The same extraction again, over a posting the user printed to PDF.
     *
     * <p>The boards that refuse a server are also the ones a person cannot copy
     * text out of - Indeed suppresses selection, and a phone has no select-all -
     * so {@link #overviewFromText} alone does not actually reach them. Print to
     * PDF is the export every browser offers, and it is the same file the user
     * wants archived against the posting anyway.
     *
     * <p>Joins {@code overviewFromText} rather than calling the model directly,
     * so the length rules and the truncation stay in one place.
     */
    public JobPostingExtraction overviewFromPdf(byte[] pdf) {
        return overviewFromText(pdfTextExtractor.extract(pdf));
    }

    /**
     * Same fetch/redirect/SSRF-validation path as {@link #overview}, but
     * returns the raw HTML instead of extracted+truncated text - for callers
     * that need the parsed DOM themselves (e.g. reading JSON-LD script tags).
     */
    public FetchedPage fetchPage(String rawUrl) {
        return fetchHtml(urlGuard.validate(rawUrl));
    }

    /** The posting's visible text, fetched through the same validated path. */
    public String postingText(String rawUrl) {
        return visibleText(fetchHtml(urlGuard.validate(rawUrl)));
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
        FetchedPage page = fetchHtml(urlGuard.validate(rawUrl));
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
                // Cause kept: it is the only thing that still distinguishes a
                // timeout from a refused connection once the message is the
                // same user-facing sentence - see FailureCategory.of.
                throw new ApiException.BadGateway(message("error.posting.unreachable"), e);
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new ApiException.BadGateway(message("error.posting.redirectNoLocation")));
                target = urlGuard.validate(target.resolve(location).toString());
                continue;
            }
            if (status < 200 || status >= 300) {
                throw upstreamFailure(status);
            }

            byte[] html = readBounded(response.body());
            return new FetchedPage(target, new String(html, StandardCharsets.UTF_8));
        }
        throw new ApiException.BadGateway(message("error.posting.tooManyRedirects"));
    }

    /**
     * A site that refuses automated access is a different problem from a broken
     * link, and saying so matters: aggregators like Indeed answer 403 to every
     * request from a server, so "check the URL and try again" sends the caller
     * back to retry an address that will never work.
     */
    ApiException upstreamFailure(int status) {
        return switch (status) {
            case 401, 403, 429 -> new ApiException.BadGateway(message("error.posting.blocked", status), status);
            case 404, 410 -> new ApiException.BadGateway(message("error.posting.notFound", status), status);
            default -> new ApiException.BadGateway(message("error.posting.upstreamError", status), status);
        };
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
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
            throw new ApiException.BadGateway(message("error.posting.readFailed"));
        }
    }

}
