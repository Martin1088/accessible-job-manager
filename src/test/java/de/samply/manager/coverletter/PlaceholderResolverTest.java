package de.samply.manager.coverletter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderResolverTest {

    private final PlaceholderResolver resolver = new PlaceholderResolver();

    private static final Map<String, String> VALUES = Map.of(
            "company", "Meier & Söhne",
            "position", "Java-Entwicklerin");

    @Test
    void substitutesValuesAndToleratesInnerWhitespace() {
        assertThat(resolver.resolvePlain("Bewerbung bei {{company}} als {{ position }}", VALUES))
                .isEqualTo("Bewerbung bei Meier & Söhne als Java-Entwicklerin");
    }

    @Test
    void escapesSubstitutedValuesWhenResolvingIntoMarkup() {
        assertThat(resolver.resolveEscaped("<b>{{company}}</b>", VALUES))
                .isEqualTo("<b>Meier &amp; Söhne</b>");
    }

    @Test
    void leavesUnknownPlaceholdersVerbatimSoThePreviewShowsThem() {
        assertThat(resolver.resolvePlain("Hallo {{unbekannt}}", VALUES))
                .isEqualTo("Hallo {{unbekannt}}");
        assertThat(resolver.unknownPlaceholders("{{company}} {{unbekannt}}", VALUES))
                .containsExactly("unbekannt");
    }

    @Test
    void doesNotReinterpretSubstitutedValuesAsPlaceholders() {
        assertThat(resolver.resolvePlain("{{company}}", Map.of("company", "{{position}}")))
                .isEqualTo("{{position}}");
    }

    @Test
    void treatsNullAsEmpty() {
        assertThat(resolver.resolvePlain(null, VALUES)).isEmpty();
    }
}
