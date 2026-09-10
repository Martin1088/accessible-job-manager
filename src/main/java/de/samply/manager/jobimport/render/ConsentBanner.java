package de.samply.manager.jobimport.render;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The JavaScript that takes a consent banner off the page before the archived
 * snapshot is printed.
 *
 * <p>Letting the site's print stylesheet hide its own banner is the first
 * choice and usually works - ofd-bw.fv-bwl.de prints clean. It is not
 * universal: bwi.de has no print rule for its consent panel, so the archived
 * record carried the whole thing twice, ahead of the posting it was supposed to
 * be a record of. Measured on that posting, removal took the snapshot from 6116
 * to 4972 characters with every posting field still present.
 *
 * <p>Gotenberg has no CSS or script injection of its own. {@code
 * waitForExpression} is the one field that evaluates JavaScript in the page,
 * and it is used here for its side effect rather than its value - which is
 * off-label enough to be worth stating plainly rather than leaving to be
 * discovered.
 *
 * <p><b>The expression must be total.</b> Gotenberg polls it until it is truthy
 * and gives up at {@code --api-timeout}, answering 503 - so an expression that
 * throws, or that ever returns false, does not degrade the snapshot, it
 * destroys it. Everything is inside a {@code try} whose only outcome is
 * {@code true}: a selector this list gets wrong costs an unremoved banner, never
 * the render.
 *
 * <p>Only {@link RenderProfile#SNAPSHOT} uses this. The extraction render is
 * text for a model that can ignore a banner, and paying a page evaluation for it
 * would buy nothing.
 */
final class ConsentBanner {

    private static final String EXPRESSION = buildExpression();

    private ConsentBanner() {}

    static String removalExpression() {
        return EXPRESSION;
    }

    private static String buildExpression() {
        String selectors = String.join(",", loadSelectors());
        // Single-quoted in JS so the selector list's own double quotes survive;
        // the file is ours, so there is no untrusted input to escape here.
        return "(function(){try{document.querySelectorAll('" + selectors
                + "').forEach(function(e){e.remove()})}catch(e){}return true})()";
    }

    private static List<String> loadSelectors() {
        List<String> selectors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("jobimport/consent-selectors.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                // "//" and not "#": "#" starts a CSS id selector, which is most
                // of this file. Getting that wrong would silently drop every id
                // rule and leave a list that looks populated but matches little.
                if (!line.isEmpty() && !line.startsWith("//")) {
                    selectors.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load jobimport/consent-selectors.txt", e);
        }
        if (selectors.isEmpty()) {
            throw new IllegalStateException("jobimport/consent-selectors.txt is empty");
        }
        return selectors;
    }
}
