package de.samply.manager.jobimport.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The last-resort tier for pages with no JobPosting-typed JSON-LD/microdata
 * and no known ATS - modeled directly on ofd-bw.fv-bwl.de's Teamleitung
 * posting (Baden-Württemberg's shared state-agency portal template), which
 * carries only an {@code "@type": "Article"} JSON-LD block and a plain-text
 * "Standort: City; ..." line.
 */
class HeuristicFieldExtractorTest {

    private final HeuristicFieldExtractor extractor = new HeuristicFieldExtractor(new ObjectMapper());

    private static ExtractionContext ctx(String html) {
        Document document = Jsoup.parse(html, "https://ofd-bw.fv-bwl.de/posting");
        return new ExtractionContext(document, document.text(), "https://ofd-bw.fv-bwl.de/posting", null);
    }

    @Test
    void readsTitleFromH1LocationFromTheStandortLabelAndCompanyFromArticleSection() {
        String html = """
                <html><head><title>Teamleitung (w/m/d) - OFD</title>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org", "@type": "Article",
                  "headline": "Teamleitung (w/m/d)",
                  "articleSection": "Oberfinanzdirektion Baden-Württemberg | Bewerbungsfrist: 04.09.2026 | bis Entgeltgruppe 6 TV-L",
                  "publisher": { "@type": "Organization", "name": "OFD" }
                }
                </script></head>
                <body>
                  <h1 class="headline headline--1">Teamleitung (w/m/d)</h1>
                  <h3 class="headline headline--6"><span>Standort: Karlsruhe; Vollzeit / Teilzeit</span></h3>
                  <p>Ihre Aufgaben: ...</p>
                </body></html>
                """;

        ExtractionResult result = extractor.extract(ctx(html));

        assertThat(result.title().value()).isEqualTo("Teamleitung (w/m/d)");
        assertThat(result.companyName().value()).isEqualTo("Oberfinanzdirektion Baden-Württemberg");
        assertThat(result.locations()).singleElement()
                .satisfies(l -> assertThat(l.city()).isEqualTo("Karlsruhe"));
        assertThat(result.title().tier()).isEqualTo(ConfidenceTier.HEURISTIC);
    }

    @Test
    void fallsBackToPublisherNameWhenThereIsNoArticleSection() {
        String html = """
                <html><head>
                <script type="application/ld+json">
                { "@type": "Article", "headline": "Sachbearbeitung", "publisher": { "name": "Landratsamt Beispiel" } }
                </script></head>
                <body><p>Standort: Freiburg, ab sofort</p></body></html>
                """;

        ExtractionResult result = extractor.extract(ctx(html));

        assertThat(result.companyName().value()).isEqualTo("Landratsamt Beispiel");
        assertThat(result.title().value()).isEqualTo("Sachbearbeitung");
        assertThat(result.locations()).singleElement()
                .satisfies(l -> assertThat(l.city()).isEqualTo("Freiburg"));
    }

    @Test
    void fallsBackToTheTitleTagWhenThereIsNoH1AndNoJsonLd() {
        String html = "<html><head><title>Sachbearbeiter (m/w/d) - Musterfirma GmbH</title></head><body></body></html>";

        ExtractionResult result = extractor.extract(ctx(html));

        assertThat(result.title().value()).isEqualTo("Sachbearbeiter (m/w/d)");
        assertThat(result.companyName().value()).isEqualTo("Musterfirma GmbH");
    }

    @Test
    void prefersOgSiteNameOverTheTitleTagSuffix() {
        String html = """
                <html><head>
                <title>Sachbearbeiter (m/w/d) - Karriereseite</title>
                <meta property="og:site_name" content="Musterfirma GmbH"/>
                </head><body></body></html>
                """;

        ExtractionResult result = extractor.extract(ctx(html));

        assertThat(result.companyName().value()).isEqualTo("Musterfirma GmbH");
    }

    @Test
    void findsNothingOnAPageWithNoSignalAtAll() {
        ExtractionResult result = extractor.extract(ctx("<html><head></head><body><p>Nothing here.</p></body></html>"));

        assertThat(result.title().present()).isFalse();
        assertThat(result.companyName().present()).isFalse();
        assertThat(result.locations()).isEmpty();
    }

    @Test
    void aDocumentLessContextYieldsAnEmptyResultRatherThanThrowing() {
        ExtractionContext ctx = new ExtractionContext(null, null, "https://example.org", null);

        ExtractionResult result = extractor.extract(ctx);

        assertThat(result.title().present()).isFalse();
    }
}
