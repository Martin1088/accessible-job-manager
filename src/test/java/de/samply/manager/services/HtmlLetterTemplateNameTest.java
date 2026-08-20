package de.samply.manager.services;

import de.samply.manager.coverletter.CoverLetterHtmlService;
import de.samply.manager.coverletter.StyleSettingsValidator;
import de.samply.manager.dto.HtmlLetterTemplateRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.HtmlLetterTemplate;
import de.samply.manager.repository.HtmlLetterTemplateRepository;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.types.Block;
import de.samply.manager.types.BlockKey;
import de.samply.manager.types.Language;
import de.samply.manager.types.LayoutLetterKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A stored letter is picked out of the documents list by its name, so a new one
 * has to carry one. Defaulting the name instead let several letters share it.
 */
class HtmlLetterTemplateNameTest {

    private final HtmlLetterTemplateRepository repository = mock(HtmlLetterTemplateRepository.class);
    private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
    private final CoverLetterHtmlService coverLetterHtmlService = mock(CoverLetterHtmlService.class);
    private final StyleSettingsValidator styleSettingsValidator = mock(StyleSettingsValidator.class);
    private final StaticMessageSource messages = new StaticMessageSource();

    private final HtmlLetterTemplateService service = new HtmlLetterTemplateService(
            repository, userProfileRepository, coverLetterHtmlService, styleSettingsValidator, messages);

    HtmlLetterTemplateNameTest() {
        messages.addMessage("error.letterTemplate.nameRequired", java.util.Locale.ROOT, "name required");
    }

    /**
     * Carries a block on purpose: an empty list would send the service to the
     * skeleton, which is a different code path than the one under test here.
     */
    private HtmlLetterTemplateRequest named(String name) {
        List<Block> blocks = List.of(new Block(UUID.randomUUID(), BlockKey.PARAGRAPH, "text", List.of()));
        return new HtmlLetterTemplateRequest(name, LayoutLetterKey.DIN5008_COVER_LETTER_B, null, blocks);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void creatingWithoutAName_isRejected(String name) {
        assertThatThrownBy(() -> service.create(named(name), Language.GERMAN, "test-sub"))
                .isInstanceOf(ApiException.BadRequest.class);

        verify(repository, never()).save(any());
    }

    @Test
    void creatingWithNoRequestAtAll_isRejected() {
        assertThatThrownBy(() -> service.create(null, Language.GERMAN, "test-sub"))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void aGivenNameIsTrimmedAndKept() {
        when(repository.save(any(HtmlLetterTemplate.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(named("  Bewerbung Muster  "), Language.GERMAN, "test-sub").name())
                .isEqualTo("Bewerbung Muster");
    }

    /** On update a blank name still means "leave the stored one alone". */
    @Test
    void updatingWithoutAName_keepsTheStoredOne() {
        HtmlLetterTemplate stored = HtmlLetterTemplate.builder()
                .id(UUID.randomUUID())
                .userId("test-sub")
                .name("Bewerbung Muster")
                .language(Language.GERMAN)
                .layoutLetter(LayoutLetterKey.DIN5008_COVER_LETTER_B)
                .blocks(List.of())
                .build();
        when(repository.findById(stored.getId())).thenReturn(Optional.of(stored));
        when(repository.save(any(HtmlLetterTemplate.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.update(stored.getId(), named("  "), Language.GERMAN, "test-sub").name())
                .isEqualTo("Bewerbung Muster");
    }
}
