package de.samply.manager.coverletter;

import de.samply.manager.types.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The suggested letter text the editor offers per language.
 *
 * <p>A missing translation does not fail: Spring falls back to the base bundle,
 * which is German. Someone writing an English letter would then be handed German
 * suggestions with nothing anywhere reporting a problem - so the distinctness is
 * asserted rather than assumed.
 */
class SkeletonMessagesTest {

    private static final List<String> SKELETON_KEYS = List.of(
            "coverLetter.skeleton.opening",
            "coverLetter.skeleton.motivation",
            "coverLetter.skeleton.outro",
            "coverLetter.defaultTemplateName");

    private final ResourceBundleMessageSource messages = messageSource();

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void everyLanguageHasEverySkeletonKey(Language language) {
        for (String key : SKELETON_KEYS) {
            assertThat(messages.getMessage(key, null, language.locale()))
                    .as("%s for %s", key, language)
                    .isNotBlank();
        }
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void noLanguageSilentlyFallsBackToAnother(Language language) {
        List<Language> others = Arrays.stream(Language.values()).filter(l -> l != language).toList();

        for (String key : SKELETON_KEYS) {
            String text = messages.getMessage(key, null, language.locale());
            for (Language other : others) {
                assertThat(text)
                        .as("%s is identical for %s and %s - a translation is probably missing",
                                key, language, other)
                        .isNotEqualTo(messages.getMessage(key, null, other.locale()));
            }
        }
    }

    /** The opening keeps the placeholders the resolver later fills in. */
    @ParameterizedTest
    @EnumSource(Language.class)
    void theOpeningKeepsItsPositionPlaceholder(Language language) {
        assertThat(messages.getMessage("coverLetter.skeleton.opening", null, language.locale()))
                .contains("{{position}}");
    }
}
