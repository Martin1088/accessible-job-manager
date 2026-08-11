package de.samply.manager.coverletter;

import de.samply.manager.types.Language;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures where the printed letter actually lands on the page.
 * <p>
 * The CSS unit tests prove the template writes the right lengths; only a real
 * Chromium print proves those lengths survive to paper. PDFBox reads the text back
 * with coordinates, which are converted from points to millimetres and compared
 * against the DIN 5008 zones. A line is expected to start at its zone top and sit
 * within one line box below it - never above.
 * <p>
 * Needs the dev Gotenberg ({@code cd dev && docker compose up -d gotenberg}); the
 * test skips itself when the service is not reachable.
 */
class Din5008PdfGeometryTest {

    private static final String GOTENBERG_URL =
            System.getProperty("gotenberg.url", System.getenv().getOrDefault("GOTENBERG_URL", "http://localhost:3000"));

    private static final double MM_PER_POINT = 25.4 / 72.0;
    /** A baseline sits below the top of its line box; 6 mm covers a 16 pt line. */
    private static final double BASELINE_TOLERANCE_MM = 6.0;
    private static final double HORIZONTAL_TOLERANCE_MM = 2.0;

    private static List<Line> firstPageLines;

    @BeforeAll
    static void renderLetter() throws IOException {
        Assumptions.assumeTrue(gotenbergReachable(), "Gotenberg not reachable at " + GOTENBERG_URL);

        CoverLetterModel letter = CoverLetterFixtures.assembler().assemble(
                CoverLetterFixtures.template(List.of(
                        CoverLetterFixtures.paragraph("Ich bewerbe mich als {{position}} bei {{company}}."))),
                CoverLetterFixtures.position(),
                Language.GERMAN);

        String html = new HtmlCoverLetterRenderer(CoverLetterFixtures.templateEngine()).render(letter);
        byte[] pdf = new HtmlToPdfConverter(GOTENBERG_URL, CoverLetterFixtures.messageSource()).toPdf(html);

        firstPageLines = readLines(pdf);
    }

    @Test
    void printsTheReturnAddressAtTheTopOfTheAddressField() {
        assertStartsInZone("Jane Doe ·", StyleSettings.din5008FormB().returnAddressTopMm());
    }

    @Test
    void printsTheRecipientInTheAnschriftzone() {
        assertStartsInZone("Muster GmbH", StyleSettings.din5008FormB().recipientTopMm());
    }

    @Test
    void printsTheSubjectAtTheDin5008SubjectLine() {
        assertStartsInZone("Bewerbung als", StyleSettings.din5008FormB().subjectTopMm());
    }

    @Test
    void printsTheInformationBlockInItsOwnColumn() {
        Line infoBlock = line("Springfield,");
        StyleSettings din = StyleSettings.din5008FormB();

        assertThat(infoBlock.topMm()).isBetween(din.infoBlockTopMm(), din.infoBlockTopMm() + BASELINE_TOLERANCE_MM);
        assertThat(infoBlock.leftMm()).isCloseTo(din.infoBlockLeftMm(),
                org.assertj.core.data.Offset.offset(HORIZONTAL_TOLERANCE_MM));
    }

    @Test
    void keepsTheWritingAreaInsideTheLeftMargin() {
        double leftMarginMm = StyleSettings.din5008FormB().leftMarginMm();

        assertThat(firstPageLines).allSatisfy(line ->
                assertThat(line.leftMm()).isGreaterThanOrEqualTo(leftMarginMm - HORIZONTAL_TOLERANCE_MM));
        assertThat(line("Muster GmbH").leftMm())
                .isCloseTo(leftMarginMm, org.assertj.core.data.Offset.offset(HORIZONTAL_TOLERANCE_MM));
    }

    private void assertStartsInZone(String prefix, double zoneTopMm) {
        assertThat(line(prefix).topMm())
                .as("'%s' must start at the %s mm zone", prefix, zoneTopMm)
                .isBetween(zoneTopMm, zoneTopMm + BASELINE_TOLERANCE_MM);
    }

    private Line line(String prefix) {
        return firstPageLines.stream()
                .filter(l -> l.text().startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No line starting with '" + prefix + "' in "
                        + firstPageLines.stream().map(Line::text).toList()));
    }

    /** One printed line with its baseline position, measured from the top left of the sheet. */
    private record Line(String text, double topMm, double leftMm) {}

    private static List<Line> readLines(byte[] pdf) throws IOException {
        List<Line> lines = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) {
                    if (!text.isBlank() && !textPositions.isEmpty()) {
                        TextPosition first = textPositions.get(0);
                        lines.add(new Line(text.strip(),
                                first.getYDirAdj() * MM_PER_POINT,
                                first.getXDirAdj() * MM_PER_POINT));
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            stripper.getText(document);
        }
        return lines;
    }

    private static boolean gotenbergReachable() {
        URI uri = URI.create(GOTENBERG_URL);
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(Optional.ofNullable(uri.getHost()).orElse("localhost"), port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
