package de.samply.manager.jobimport.render;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * What a rendered posting is going to be used for, which is what decides how
 * Chromium should print it.
 *
 * <p>The two uses genuinely want different output, so they cannot share one
 * render. Extraction wants the page as one continuous surface, because every
 * page break splits a sentence in the text PDFBox reads back. A snapshot is a
 * document a person opens, downloads and prints, so it wants ordinary A4 pages.
 *
 * <p>They also disagree about {@code emulatedMediaType}, which is less obvious
 * and was got wrong once already. Rendering the archived snapshot with screen
 * styles produced a PDF with the site's cookie-consent banner sitting across
 * the posting and lines cut off at the right margin, because a screen layout is
 * not built to be put on paper - which is precisely what a print stylesheet is
 * for. So the snapshot asks for print and lets the site do that job.
 *
 * <p>Extraction asks for screen for the opposite reason: nobody reads it, so a
 * cookie banner in the text costs only tokens, while a site whose print
 * stylesheet hides the posting body would cost the import entirely. Note the
 * media type does not affect whether JavaScript runs - both profiles get the
 * rendered page, which is the whole point of going through Chromium.
 */
public enum RenderProfile {

    /**
     * Text for the LLM. One page, no backgrounds: nothing here is ever shown to
     * anyone, so ink that only costs render time is left off.
     */
    EXTRACTION {
        @Override
        void addTo(MultiValueMap<String, Object> body) {
            body.add("emulatedMediaType", "screen");
            body.add("singlePage", "true");
            body.add("printBackground", "false");
        }
    },

    /**
     * The archived record. Tagged, because it is a document this application
     * stores and hands back to people.
     *
     * <p>It is tagged but deliberately <em>not</em> marked as PDF/UA-1. Tagging
     * is a real improvement whatever the source markup is. The identification
     * is a claim that the document conforms, and the structure here comes
     * entirely from a third party's HTML that nothing has checked - so stamping
     * it would make an alt-less, heading-less job ad announce itself as
     * accessible to assistive technology that trusts the flag. Gotenberg 8.36
     * would write it for us with {@code pdfua=true}; the omission is a choice,
     * not a limitation.
     */
    SNAPSHOT {
        @Override
        void addTo(MultiValueMap<String, Object> body) {
            body.add("emulatedMediaType", "print");
            body.add("printBackground", "true");
            body.add("generateTaggedPdf", "true");
            body.add("waitForExpression", ConsentBanner.removalExpression());
        }
    };

    abstract void addTo(MultiValueMap<String, Object> body);

    /**
     * The multipart body for one render of {@code url}, profile-specific fields
     * included.
     *
     * <p>{@code skipNetworkIdleEvent=false} is what makes this wait for the page
     * to settle rather than printing whatever had arrived when load fired -
     * which is the whole reason a JavaScript-rendered posting is readable here
     * and not through a plain GET.
     */
    MultiValueMap<String, Object> body(String url, String waitDelay) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("url", url);
        body.add("skipNetworkIdleEvent", "false");
        if (waitDelay != null && !waitDelay.isBlank()) {
            body.add("waitDelay", waitDelay);
        }
        body.add("failOnHttpStatusCodes", "[499,599]");
        addTo(body);
        return body;
    }
}
