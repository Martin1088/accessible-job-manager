package de.samply.manager.jobimport.render;

import de.samply.manager.exception.ApiException;
import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The URL refusal used to be asserted through {@code JobPostingSnapshotService};
 * it moved here with the render itself, so the guarantee is tested where it now
 * lives rather than through a service that no longer does the validating.
 */
@ExtendWith(MockitoExtension.class)
class PostingRendererTest {

    @Mock ImportDiagnostics diagnostics;

    /**
     * The guard cannot constrain Gotenberg - Chromium fetches the page itself,
     * from its own container, following its own redirects - but it must at
     * least stop the obvious internal URL before it is handed over. Anything
     * past that is the network isolation described in Readme.md.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://172.17.0.2:5432/",  // a sibling container on the Docker bridge, e.g. postgres
            "http://127.0.0.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.5/",
            "ftp://example.com/",
    })
    void refusesADisallowedUrlWithoutCallingGotenberg(String url) {
        PostingRenderer renderer = RenderFixtures.refusingRenderer(diagnostics);

        assertThatThrownBy(() -> renderer.render(url, "user-1", RenderProfile.SNAPSHOT))
                .isInstanceOf(ApiException.BadRequest.class);

        // A URL that was never fetched is not an import attempt, so it must not
        // land on the diagnostics work list as one.
        verifyNoInteractions(diagnostics);
    }

    /** The refusal is the same whichever profile asked for the render. */
    @Test
    void refusesADisallowedUrlForTheExtractionProfileToo() {
        PostingRenderer renderer = RenderFixtures.refusingRenderer(diagnostics);

        assertThatThrownBy(() -> renderer.render("http://127.0.0.1/", "user-1", RenderProfile.EXTRACTION))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    /**
     * The two profiles must not be interchangeable: extraction asks for one
     * continuous page so no sentence is split across a page break, the snapshot
     * asks for a tagged, ordinary document. A regression that collapsed them
     * would be invisible until someone read a PDF.
     */
    @Test
    void theProfilesDifferInTheFieldsTheySend() {
        var extraction = RenderProfile.EXTRACTION.body("https://example.com/job", "1s");
        var snapshot = RenderProfile.SNAPSHOT.body("https://example.com/job", "1s");

        assertThat(extraction.getFirst("singlePage")).isEqualTo("true");
        assertThat(snapshot.get("singlePage")).isNull();
        assertThat(snapshot.getFirst("generateTaggedPdf")).isEqualTo("true");
        assertThat(extraction.get("generateTaggedPdf")).isNull();
    }

    /**
     * The snapshot is rendered with print styles and the extraction with screen,
     * and this must not be "simplified" into one shared value - it was, once.
     * Screen styles put the site's cookie-consent banner across the archived
     * posting and cut lines off at the right margin, because a screen layout is
     * not built for paper. Extraction wants the opposite trade: a banner in the
     * text costs tokens, whereas a print stylesheet that hides the posting body
     * costs the whole import.
     */
    @Test
    void theSnapshotPrintsWhileTheExtractionUsesScreenStyles() {
        assertThat(RenderProfile.SNAPSHOT.body("https://example.com/job", "1s").getFirst("emulatedMediaType"))
                .isEqualTo("print");
        assertThat(RenderProfile.EXTRACTION.body("https://example.com/job", "1s").getFirst("emulatedMediaType"))
                .isEqualTo("screen");
    }

    /** Waiting for the page to settle is what makes a JS-rendered posting readable at all. */
    @ParameterizedTest
    @ValueSource(strings = {"EXTRACTION", "SNAPSHOT"})
    void everyProfileWaitsForTheNetworkToSettle(String profileName) {
        var body = RenderProfile.valueOf(profileName).body("https://example.com/job", "1s");

        assertThat(body.getFirst("skipNetworkIdleEvent")).isEqualTo("false");
        assertThat(body.getFirst("waitDelay")).isEqualTo("1s");
        assertThat(body.getFirst("url")).isEqualTo("https://example.com/job");
    }

    /** An unset wait delay is left out rather than sent empty, which Gotenberg rejects. */
    @Test
    void aBlankWaitDelayIsOmitted() {
        assertThat(RenderProfile.EXTRACTION.body("https://example.com/job", "  ").get("waitDelay")).isNull();
        assertThat(RenderProfile.EXTRACTION.body("https://example.com/job", null).get("waitDelay")).isNull();
    }
}
