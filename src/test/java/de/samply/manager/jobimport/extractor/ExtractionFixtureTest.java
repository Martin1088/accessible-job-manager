package de.samply.manager.jobimport.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the whole extractor chain over saved postings and writes each run's
 * tier-by-tier report to {@code build/reports/postings/}.
 *
 * <p>This replaces {@code POST /api/posting/extractors/test}, which reached the
 * same pipeline through an HTTP endpoint that shipped in the production
 * artifact. Reading the report off disk costs nothing that endpoint gave, and
 * gains the case it never could serve: a board that answers 403 to a server.
 * {@code JobPostingParserService.upstreamFailure} documents Indeed as exactly
 * that, so a live-URL debug run could only ever show its 502 - the page has to
 * come from a browser either way.
 *
 * <p><b>Adding a posting:</b> save the page, drop it in
 * {@code src/test/resources/postings/} as {@code <name>.html}, and add
 * {@code <name>.properties} next to it with at least {@code url=}. Run
 *
 * <pre>./gradlew test --tests "*ExtractionFixtureTest"</pre>
 *
 * and read {@code build/reports/postings/<name>.txt}. Once the parse is right,
 * add {@code expect.title=…} and friends to the same properties file to hold it
 * there - see {@link PostingFixture} for the recognised field names.
 *
 * <p>The ATS tier is constructed with <b>no adapters</b>. Ashby, Personio,
 * OracleHCM and Comeet each hold a {@code RestClient} and call the board's API,
 * which a fixture run must not do; their coverage lives in
 * {@link ComeetAdapterTest}, which stubs the HTTP with
 * {@code MockRestServiceServer}. Every other tier is a pure function of the
 * parsed document, so this pipeline touches no network at all.
 */
class ExtractionFixtureTest {

    private static final Path REPORT_DIR = Path.of("build", "reports", "postings");

    private final ObjectMapper mapper = new ObjectMapper();

    private final JobPostingExtractionPipeline pipeline = new JobPostingExtractionPipeline(List.of(
            new JsonLdJobPostingExtractor(mapper),
            new AtsApiExtractor(List.of()),
            new MicrodataJobPostingExtractor(),
            new ContactBlockExtractor(new SalutationLookup()),
            new HeuristicFieldExtractor(mapper)));

    @TestFactory
    Stream<DynamicTest> savedPostingsExtractWhatTheyDeclare() {
        List<PostingFixture> fixtures = PostingFixture.all();
        if (fixtures.isEmpty()) {
            return Stream.of(DynamicTest.dynamicTest(
                    "no fixtures in src/test/resources/postings",
                    () -> { /* nothing saved yet - not a failure */ }));
        }
        return fixtures.stream().map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> run(fixture)));
    }

    private void run(PostingFixture fixture) {
        var document = fixture.document();
        ExtractionDebugReport report = pipeline.runDebug(
                document, document.text(), fixture.url(), fixture.boardHint());

        String formatted = PostingFixture.format(fixture, report);
        Path written = write(fixture.name(), formatted);

        Map<String, String> expected = fixture.expectations();
        if (expected.isEmpty()) {
            // Nothing declared yet: the run is the deliverable, not a verdict.
            return;
        }

        Map<String, String> actual = PostingFixture.flatten(report.merged());
        assertThat(actual)
                .describedAs("%s - full tier report: %s%n%n%s", fixture.name(), written, formatted)
                .containsAllEntriesOf(expected);
    }

    private Path write(String name, String content) {
        try {
            Files.createDirectories(REPORT_DIR);
            Path file = REPORT_DIR.resolve(name + ".txt");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
