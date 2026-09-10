package de.samply.manager.jobimport.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expression is sent to Gotenberg's {@code waitForExpression}, which polls
 * it until truthy and answers 503 at {@code --api-timeout} if it never is. That
 * makes totality the property worth testing: a selector this list gets wrong
 * should cost an unremoved banner, never the snapshot.
 */
class ConsentBannerTest {

    private final String expression = ConsentBanner.removalExpression();

    @Test
    void alwaysReturnsTrueEvenWhenTheDomWorkThrows() {
        // Everything is inside try/catch and the return sits outside it, so no
        // path through the expression can yield anything but true. An expression
        // that could throw would turn every snapshot into a 503.
        assertThat(expression).contains("try{");
        assertThat(expression).contains("catch(e){}");
        assertThat(expression).endsWith("return true})()");
    }

    @Test
    void carriesTheSelectorsFromTheResourceFile() {
        assertThat(expression).contains("#cookie-banner");
        assertThat(expression).contains(".cc-window");
        // bwi.de's cookieman modal and its separate external-link notice - the
        // case the list was written for, and the reason its banner text landed
        // in the archive twice. Both read off the served markup: selectors
        // guessed from the word "cookie" matched nothing there.
        assertThat(expression).contains(".cmOverlay");
        assertThat(expression).contains(".linkOverlayNotice");
    }

    /**
     * "#" begins a CSS id selector, so a loader that treated it as a comment
     * marker would drop every id rule and leave a list that looks populated
     * while matching almost nothing.
     */
    @Test
    void idSelectorsSurviveCommentStripping() {
        assertThat(expression).contains("#onetrust-consent-sdk");
        assertThat(expression).doesNotContain("//");
    }

    /**
     * The selector list is embedded in a single-quoted JS string, so a selector
     * containing a single quote would end it early and produce a syntax error -
     * which, being a throw, is exactly the 503 case above.
     */
    @Test
    void noSelectorBreaksOutOfTheJavascriptString() {
        assertThat(expression).doesNotContain("\\'");
        String selectors = expression.substring(
                expression.indexOf("querySelectorAll('") + "querySelectorAll('".length(),
                expression.indexOf("')."));
        assertThat(selectors).doesNotContain("'");
    }

    @Test
    void onlyTheSnapshotProfileAsksForIt() {
        assertThat(RenderProfile.SNAPSHOT.body("https://example.com/job", "1s")
                .getFirst("waitForExpression")).isEqualTo(expression);
        // The extraction render feeds a model that can ignore a banner; paying a
        // page evaluation for it would buy nothing.
        assertThat(RenderProfile.EXTRACTION.body("https://example.com/job", "1s")
                .get("waitForExpression")).isNull();
    }
}
