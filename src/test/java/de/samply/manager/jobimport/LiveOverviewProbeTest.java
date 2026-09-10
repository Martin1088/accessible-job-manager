package de.samply.manager.jobimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.dto.JobPostingExtraction;
import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.jobimport.llm.JobPostingLlmClient;
import de.samply.manager.jobimport.llm.OllamaJobPostingLlmClient;
import de.samply.manager.jobimport.render.PostingRenderer;
import de.samply.manager.jobimport.render.RenderCache;
import de.samply.manager.jobimport.render.RenderFixtures;
import de.samply.manager.jobimport.render.RenderProfile;
import de.samply.manager.security.OutboundUrlGuard;
import de.samply.manager.testing.DevServices;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Runs the real {@code POST /api/posting/overview} chain against a live URL and
 * writes down what came back.
 *
 * <pre>./gradlew test --tests "*LiveOverviewProbeTest" -Dposting.url=https://…</pre>
 *
 * <p>Opt-in: without {@code -Dposting.url} it skips, so an ordinary
 * {@code ./gradlew test} and CI make no outbound request. Note the property only
 * arrives here because {@code build.gradle} forwards it into the test JVM.
 *
 * <p>The chain is the production one, assembled by hand the way the two
 * cover-letter Gotenberg tests assemble theirs - neither is Spring-context
 * based, and a context here would pull in Postgres:
 *
 * <pre>
 *   PostingRenderer.render(url, userId, EXTRACTION)  -&gt; PDF, via Gotenberg Chromium
 *   PostingPdfTextExtractor.extract(pdf)             -&gt; plain text (PDFTextStripper)
 *   JobPostingLlmClient.extract(text)                -&gt; JobPostingExtraction
 * </pre>
 *
 * <p><b>This path yields no markup.</b> Gotenberg returns a PDF and the only
 * thing read back out of it is text, so the JSON-LD and microdata tiers cannot
 * run on it - which is why {@code full-chain} still does its own HTML fetch (see
 * {@code JobPostingParserService}). What this probe shows is the LLM result,
 * never a per-tier report. For the tier chain, use {@code ExtractionFixtureTest}
 * with a saved page.
 *
 * <p>Assertions are deliberately thin. The model's output is not deterministic,
 * so the report is the deliverable and only the mechanical steps are asserted:
 * that a render came back, and that it held enough text to be worth sending. A
 * hard assertion on extracted fields would be a flaky test, not a guard.
 */
class LiveOverviewProbeTest {

    private static final Path REPORT = Path.of("build", "reports", "postings", "live-overview.txt");

    /** Any stable string: it only keys {@link RenderCache}, which starts empty here. */
    private static final String USER_ID = "live-probe";

    private static final int CONNECT_TIMEOUT_SECONDS =
            Integer.getInteger("llm.connect-timeout-seconds", 5);
    private static final int READ_TIMEOUT_SECONDS =
            Integer.getInteger("llm.read-timeout-seconds", 120);
    private static final int MAX_OUTPUT_TOKENS =
            Integer.getInteger("llm.max-output-tokens", 300);

    private final MessageSource messages = RenderFixtures.messages();

    @Test
    void rendersAndExtractsTheGivenPosting() {
        String url = System.getProperty("posting.url");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "set -Dposting.url=<job posting url> to run this probe");
        Assumptions.assumeTrue(DevServices.gotenbergReachable(),
                "Gotenberg not reachable at " + DevServices.gotenbergUrl()
                        + " - cd dev && docker compose up -d gotenberg");

        StringBuilder report = new StringBuilder("url       : ").append(url).append('\n');

        byte[] pdf = render(url, report);
        assertThat(pdf).as("Gotenberg returned an empty render").isNotEmpty();

        String text = new PostingPdfTextExtractor(messages).extract(pdf);
        report.append("text      : ").append(text.length()).append(" chars\n")
                .append("\n--- TEXT AS THE MODEL SEES IT ---\n").append(text).append('\n');

        // The same floor PostingPdfTextExtractor and overviewFromText both apply;
        // below it the page rendered but carried nothing worth extracting.
        assertThat(text.length()).as("rendered page held too little text to extract from").isGreaterThanOrEqualTo(120);

        report.append("\n--- LLM RESULT ---\n").append(llmResult(text)).append('\n');
        write(report.toString());
    }

    private byte[] render(String url, StringBuilder report) {
        PostingRenderer renderer = new PostingRenderer(
                RestClient.create(), DevServices.gotenbergUrl(), "1s", messages,
                new ImportDiagnostics(), new OutboundUrlGuard(messages, 30),
                new RenderCache(120, 32, 134_217_728L));
        try {
            byte[] pdf = renderer.render(url, USER_ID, RenderProfile.EXTRACTION);
            report.append("render    : ").append(pdf.length).append(" bytes\n");
            return pdf;
        } catch (ApiException.BadRequest e) {
            // Gotenberg answers 409 when the page itself did not load, and
            // PostingRenderer carries that page's own status through. A 401/403
            // is the board refusing this host - a different problem from a
            // broken pipeline, and the distinction is why this probe exists.
            Integer upstream = e.getUpstreamStatus();
            String reason = upstream != null && (upstream == 401 || upstream == 403 || upstream == 429)
                    ? "the board refused this host (HTTP " + upstream + "). Not a pipeline failure: it blocks "
                      + "automated access from this network. Use a printed PDF via /overview-pdf instead."
                    : "the page did not load" + (upstream == null ? "" : " (HTTP " + upstream + ")");
            return fail("Could not render %s - %s%n%s", url, reason, e.getMessage());
        }
    }

    private String llmResult(String text) {
        if (!DevServices.ollamaReachable()) {
            return "(skipped - Ollama not reachable at " + DevServices.ollamaUrl()
                    + "; cd dev && docker compose -f local-setup.yml up -d ollama)";
        }
        JobPostingLlmClient client = new OllamaJobPostingLlmClient(
                DevServices.ollamaUrl(), DevServices.ollamaModel(),
                CONNECT_TIMEOUT_SECONDS, READ_TIMEOUT_SECONDS, MAX_OUTPUT_TOKENS,
                new ObjectMapper(), messages);
        // Elapsed time is reported either way. A model that answers correctly in
        // four minutes and one that answers in ten seconds look identical in the
        // extracted fields, and the difference is the whole story.
        long startedAt = System.nanoTime();
        try {
            JobPostingExtraction extraction = client.extract(text);
            return """
                    model         : %s
                    elapsed       : %.1fs
                    title         : %s
                    company       : %s
                    location      : %s
                    employmentType: %s""".formatted(
                    DevServices.ollamaModel(), elapsedSeconds(startedAt), extraction.title(),
                    extraction.company(), extraction.location(), extraction.employmentType());
        } catch (ApiException e) {
            // The render is the expensive half and it succeeded; losing the
            // report because the model was busy would waste it.
            return "(failed after %.1fs: %s)".formatted(elapsedSeconds(startedAt), e.getMessage());
        }
    }

    private static double elapsedSeconds(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000_000.0;
    }

    private void write(String content) {
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, content, StandardCharsets.UTF_8);
            System.out.println("live overview probe written to " + REPORT.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
