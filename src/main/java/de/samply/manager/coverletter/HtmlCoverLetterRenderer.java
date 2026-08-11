package de.samply.manager.coverletter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

/**
 * Renders an assembled letter to the DIN 5008 HTML document that Gotenberg prints.
 * <p>
 * The template lives in {@code src/main/resources/templates/cover-letter/} and never
 * leaves the server: the layout is an invariant of this application, not something a
 * client can supply or modify. All measurements are injected as pre-formatted CSS
 * lengths ({@link CssLengths}) derived from the validated {@link StyleSettings}.
 */
@Component
@RequiredArgsConstructor
public class HtmlCoverLetterRenderer {

    private static final String TEMPLATE = "cover-letter/din5008";

    private final SpringTemplateEngine templateEngine;

    public String render(CoverLetterModel model) {
        Context context = new Context(Locale.ROOT);
        context.setVariable("letter", model);
        context.setVariable("css", CssLengths.of(model.style()));
        return templateEngine.process(TEMPLATE, context);
    }
}
