package de.samply.manager.coverletter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkupSanitizerTest {

    private final MarkupSanitizer sanitizer = new MarkupSanitizer();

    @Test
    void keepsInlineEmphasisAndLinks() {
        String clean = sanitizer.sanitize("<p>Ich bin <b>sehr</b> <em>motiviert</em>.<br>Mehr: "
                + "<a href=\"https://example.com\">Portfolio</a></p>");

        assertThat(clean)
                .contains("<b>sehr</b>")
                .contains("<em>motiviert</em>")
                .contains("<br>")
                .contains("href=\"https://example.com\"")
                .contains("rel=\"noopener noreferrer\"")
                .doesNotContain("<p>"); // block-level markup would break the DIN layout
    }

    @Test
    void dropsLayoutAndScriptMarkup() {
        String clean = sanitizer.sanitize(
                "<div style=\"position:absolute;top:0\">weg</div>"
                        + "<script>alert(1)</script>"
                        + "<img src=x onerror=alert(1)>"
                        + "<b onclick=\"steal()\">bleibt</b>");

        assertThat(clean)
                .contains("<b>bleibt</b>")
                .doesNotContain("<div", "style=", "<script", "alert", "<img", "onclick");
    }

    /**
     * The anchors the cover letter form's "Insert link" action writes, exactly
     * as it writes them: text and URL HTML-escaped there, so the two sides agree
     * on what reaches this sanitizer.
     */
    @Test
    void keepsAnchorsProducedByTheEditor() {
        String clean = sanitizer.sanitize(
                "Vor <a href=\"https://example.com/a?x=1&amp;y=2\">Tom &amp; Jerry</a> nach");

        assertThat(clean)
                .contains("href=\"https://example.com/a?x=1&amp;y=2\"")
                .contains(">Tom &amp; Jerry<");
    }

    @Test
    void keepsMailtoLinks() {
        assertThat(sanitizer.sanitize("<a href=\"mailto:chef@firma.de\">Mail</a>"))
                .contains("href=\"mailto:chef@firma.de\"");
    }

    /**
     * A quote in the link text cannot close the href and start an attribute of
     * its own - the editor escapes it, and it stays escaped through here.
     */
    @Test
    void aQuoteInAUrlCannotBreakOutOfTheAttribute() {
        String clean = sanitizer.sanitize(
                "<a href=\"https://x.de/&quot;onmouseover=alert(1)\">x</a>");

        // The payload stays inside the href value: escaped, and with no quote
        // ending the attribute in front of it, so no handler attribute appears.
        assertThat(clean)
                .contains("&quot;onmouseover=alert(1)")
                .doesNotContain("\" onmouseover");
    }

    @Test
    void dropsJavascriptUrls() {
        assertThat(sanitizer.sanitize("<a href=\"javascript:alert(1)\">klick</a>"))
                .doesNotContain("javascript:")
                .contains("klick");
    }

    @Test
    void leavesPlaceholdersUntouchedSoTheyCanBeResolvedAfterwards() {
        assertThat(sanitizer.sanitize("Bewerbung bei {{company}} als <b>{{position}}</b>"))
                .isEqualTo("Bewerbung bei {{company}} als <b>{{position}}</b>");
    }

    @Test
    void escapesMarkupCharactersInPlainText() {
        assertThat(sanitizer.sanitize("Meier & Söhne <nicht ein Tag>"))
                .contains("&amp;")
                .doesNotContain("<nicht");
    }

    @Test
    void treatsNullAndBlankAsEmpty() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEmpty();
    }
}
