package de.samply.manager.coverletter;

import de.samply.manager.types.Language;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates a generated cover letter against PDF/UA-1.
 * <p>
 * This is the counterpart to {@link Din5008PdfGeometryTest}, and the two ask different
 * questions of the same file. That test reads coordinates and proves the letter is laid
 * out correctly; this one reads the structure tree and proves it can be <em>read</em> -
 * tag order, document title, declared natural language. A PDF can satisfy every DIN 5008
 * measurement and still reach a screen reader as an unordered bag of glyphs, which is
 * exactly the gap that geometry assertions cannot see.
 * <p>
 * Needs the dev Gotenberg ({@code cd dev && docker compose up -d gotenberg}); the test
 * skips itself when the service is not reachable, the same way the geometry test does.
 * veraPDF itself is an ordinary test dependency, so no external CLI has to be installed.
 */
class CoverLetterPdfUaTest {

    private static final String GOTENBERG_URL =
            System.getProperty("gotenberg.url", System.getenv().getOrDefault("GOTENBERG_URL", "http://localhost:3000"));

    private static byte[] pdf;

    @BeforeAll
    static void renderLetter() {
        Assumptions.assumeTrue(gotenbergReachable(), "Gotenberg not reachable at " + GOTENBERG_URL);
        VeraGreenfieldFoundryProvider.initialise();

        CoverLetterModel letter = CoverLetterFixtures.assembler().assemble(
                CoverLetterFixtures.template(List.of(
                        CoverLetterFixtures.paragraph("Ich bewerbe mich als {{position}} bei {{company}}."))),
                CoverLetterFixtures.position(),
                Language.GERMAN);

        String html = new HtmlCoverLetterRenderer(CoverLetterFixtures.templateEngine()).render(letter);
        pdf = new HtmlToPdfConverter(GOTENBERG_URL, CoverLetterFixtures.messageSource()).toPdf(html);
    }

    @Test
    void theGeneratedLetterIsValidPdfUa1() throws Exception {
        ValidationResult result = validate();

        assertThat(result.isCompliant())
                .withFailMessage("PDF/UA-1 validation failed:%n%s", describe(result))
                .isTrue();
    }

    private ValidationResult validate() throws Exception {
        try (PDFAParser parser = Foundries.defaultInstance()
                     .createParser(new ByteArrayInputStream(pdf), PDFAFlavour.PDFUA_1);
             PDFAValidator validator = Foundries.defaultInstance()
                     .createValidator(PDFAFlavour.PDFUA_1, false)) {
            return validator.validate(parser);
        }
    }

    /**
     * veraPDF reports one assertion per failing object, so a single missing tag can
     * produce hundreds of near-identical lines. Collapsing them to one line per rule
     * with a count keeps a failure readable - the rule id is what you act on, the
     * individual object numbers are not.
     */
    private static String describe(ValidationResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> messages = new LinkedHashMap<>();

        for (TestAssertion assertion : result.getTestAssertions()) {
            if (assertion.getStatus() == TestAssertion.Status.PASSED) {
                continue;
            }
            String rule = assertion.getRuleId().getClause() + "-" + assertion.getRuleId().getTestNumber();
            counts.merge(rule, 1, Integer::sum);
            messages.putIfAbsent(rule, assertion.getMessage());
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(e -> "  %s (x%d): %s".formatted(e.getKey(), e.getValue(), messages.get(e.getKey())))
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("  no failed assertions reported");
    }

    private static boolean gotenbergReachable() {
        try (Socket socket = new Socket()) {
            URI uri = URI.create(GOTENBERG_URL);
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            socket.connect(new InetSocketAddress(uri.getHost(), port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
