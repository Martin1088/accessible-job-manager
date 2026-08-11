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
