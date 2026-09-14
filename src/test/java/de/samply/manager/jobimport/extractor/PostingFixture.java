package de.samply.manager.jobimport.extractor;

import de.samply.manager.dto.CompanyDto;
import de.samply.manager.dto.CompanyLocationDto;
import de.samply.manager.dto.CompanyPositionDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * One saved job posting on disk, plus the two things a run over it needs that
 * the HTML itself cannot carry: the URL it came from, and what it is expected
 * to yield.
 *
 * <p>A fixture is a pair of files under {@code src/test/resources/postings/}:
 *
 * <pre>
 *   indeed-viewjob.html         the page, saved from the browser
 *   indeed-viewjob.properties   url= (required), boardHint= (optional),
 *                               expect.&lt;field&gt;= (any number, optional)
 * </pre>
 *
 * <p>The URL is not decoration. Jsoup resolves relative links against it, the
 * ATS tier dispatches on its host, and several heuristics read the path - so a
 * fixture run against the wrong URL is not the same extraction the server would
 * have performed.
 *
 * <p>{@code expect.*} keys are optional on purpose. A fixture with none is
 * reported but not asserted, which is the state a posting is in while its
 * extraction is still being worked on; adding the keys afterwards is what turns
 * a fixed parse into a guarded one.
 */
record PostingFixture(String name, String html, Properties config) {

    private static final String DIR = "postings";

    /** Every fixture on the classpath, in file-name order. */
    static List<PostingFixture> all() {
        URL dir = PostingFixture.class.getClassLoader().getResource(DIR);
        if (dir == null) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.filter(p -> p.getFileName().toString().endsWith(".html"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.html$", ""))
                    .sorted()
                    .map(PostingFixture::load)
                    .toList();
        } catch (IOException | java.net.URISyntaxException e) {
            throw new IllegalStateException("Could not list " + DIR, e);
        }
    }

    static PostingFixture load(String name) {
        return new PostingFixture(name, read(name + ".html"), properties(name + ".properties"));
    }

    String url() {
        String url = config.getProperty("url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    name + ".properties must set url= - the extraction depends on it "
                            + "(link resolution, ATS host dispatch, path heuristics)");
        }
        return url;
    }

    String boardHint() {
        return config.getProperty("boardHint");
    }

    Document document() {
        return Jsoup.parse(html, url());
    }

    /** The {@code expect.<field>} keys, field name to expected value. */
    Map<String, String> expectations() {
        Map<String, String> expected = new LinkedHashMap<>();
        for (String key : config.stringPropertyNames()) {
            if (key.startsWith("expect.")) {
                expected.put(key.substring("expect.".length()), config.getProperty(key).strip());
            }
        }
        return expected;
    }

    /**
     * The merged posting flattened to the same field names {@code expect.*}
     * uses, so an expectation is compared against one value rather than a walk
     * through the DTO tree. {@code city} and {@code street} read the first
     * location: a posting with several is rare, and asserting on all of them
     * belongs in a hand-written test rather than a properties file.
     */
    static Map<String, String> flatten(JobPosting posting) {
        Map<String, String> flat = new LinkedHashMap<>();
        CompanyDto company = posting.company();
        if (company == null) {
            return flat;
        }
        put(flat, "companyName", company.getName());

        List<CompanyPositionDto> positions = company.getPositions();
        if (positions != null && !positions.isEmpty()) {
            CompanyPositionDto position = positions.getFirst();
            put(flat, "title", position.getTitle());
            put(flat, "contactLastName", position.getContactLastName());
            put(flat, "contactEmail", position.getEmail());
            put(flat, "contactGender", position.getContactGender());
        }

        List<CompanyLocationDto> locations = company.getLocations();
        if (locations != null && !locations.isEmpty()) {
            CompanyLocationDto location = locations.getFirst();
            put(flat, "city", location.getCity());
            put(flat, "street", location.getStreet());
        }

        put(flat, "sourceJobId", posting.sourceJobId());
        put(flat, "postedAt", posting.postedAt());
        put(flat, "deadline", posting.deadline());
        put(flat, "employmentType", posting.employmentType());
        return flat;
    }

    /**
     * The whole run as text: every tier's raw output, then the merged result
     * with the tier each field came from.
     *
     * <p>This is what the removed {@code POST /api/posting/extractors/test}
     * endpoint existed to show. It is written to a file per fixture rather than
     * logged, so it is there to read after a green run too - a passing
     * extraction that quietly dropped a field is the case a failure message
     * never reaches.
     */
    static String format(PostingFixture fixture, ExtractionDebugReport report) {
        StringBuilder out = new StringBuilder();
        out.append("fixture : ").append(fixture.name()).append('\n');
        out.append("url     : ").append(fixture.url()).append('\n');
        if (fixture.boardHint() != null) {
            out.append("hint    : ").append(fixture.boardHint()).append('\n');
        }

        out.append("\nTIERS (in pipeline order, every tier run - no early exit)\n");
        for (ExtractionDebugReport.TierResult tier : report.tierResults()) {
            out.append("  ").append(tier.extractorClass())
                    .append("  [").append(tier.defaultTier()).append("]\n");
            if (tier.fields().isEmpty()) {
                out.append("      - nothing -\n");
                continue;
            }
            tier.fields().forEach((field, value) ->
                    out.append("      ").append(pad(field)).append(" = ").append(value).append('\n'));
        }

        Map<String, ConfidenceTier> provenance = report.merged().provenance();
        out.append("\nMERGED\n");
        Map<String, String> flat = flatten(report.merged());
        if (flat.isEmpty()) {
            out.append("  - nothing extracted -\n");
        }
        flat.forEach((field, value) -> {
            out.append("  ").append(pad(field)).append(" = ").append(value);
            ConfidenceTier tier = provenance.get(field);
            if (tier != null) {
                out.append("   [").append(tier).append(']');
            }
            out.append('\n');
        });

        // The same three fields ConfidenceMergedPosting.isComplete() asks for,
        // which is what decides whether the production pipeline stops early.
        List<String> missing = new ArrayList<>();
        if (!flat.containsKey("title")) missing.add("title");
        if (!flat.containsKey("companyName")) missing.add("companyName");
        if (!flat.containsKey("city") && !flat.containsKey("street")) missing.add("location");
        out.append("\nincomplete: ").append(missing.isEmpty() ? "no - all required fields present" : missing)
                .append('\n');
        return out.toString();
    }

    private static String pad(String field) {
        return String.format(Locale.ROOT, "%-16s", field);
    }

    private static void put(Map<String, String> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).strip();
        if (!text.isEmpty()) {
            target.put(key, text);
        }
    }

    private static String read(String file) {
        try (InputStream in = resource(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties properties(String file) {
        Properties props = new Properties();
        try (InputStream in = resource(file);
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }

    private static InputStream resource(String file) {
        InputStream in = PostingFixture.class.getClassLoader().getResourceAsStream(DIR + "/" + file);
        if (in == null) {
            throw new IllegalStateException("Missing fixture file: src/test/resources/" + DIR + "/" + file);
        }
        return in;
    }
}
