package de.ajm.manager.coverletter.template;

import de.ajm.manager.coverletter.render.CoverLetterHtmlService;
import de.ajm.manager.coverletter.render.StyleSettings;
import de.ajm.manager.coverletter.render.StyleSettingsValidator;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.profile.UserProfileRepository;
import de.ajm.manager.types.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the service returns after a write, checked against a real persistence context.
 * {@link HtmlLetterTemplateNameTest} mocks the repository, and a mock never flushes -
 * so it cannot see that a UUID-keyed entity is inserted only at flush, or that
 * {@code @Version} and {@code @UpdateTimestamp} move only then. The service flushes
 * before mapping to the DTO; without that, both responses below would be stale.
 *
 * <p>The whole test is one transaction ({@code @DataJpaTest}), which is exactly the
 * situation inside the service: the commit comes after the DTO has been built.
 */
@DataJpaTest
class HtmlLetterTemplatePersistenceTest {

    @Autowired HtmlLetterTemplateRepository repository;

    private HtmlLetterTemplateService service;

    @BeforeEach
    void setUp() {
        StyleSettingsValidator validator = mock(StyleSettingsValidator.class);
        when(validator.validated(any())).thenReturn(StyleSettings.din5008FormB());
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        service = new HtmlLetterTemplateService(repository, mock(UserProfileRepository.class),
                mock(CoverLetterHtmlService.class), validator, messages);
    }

    private static HtmlLetterTemplateRequest request(String name) {
        return request(name, null);
    }

    private static HtmlLetterTemplateRequest request(String name, Long version) {
        return new HtmlLetterTemplateRequest(name, LayoutLetterKey.DIN5008_COVER_LETTER_B, null,
                List.of(new Block(UUID.randomUUID(), BlockKey.PARAGRAPH, "Text", null)), version);
    }

    @Test
    void createReturnsTheCreationTimestamp() {
        HtmlLetterTemplateDto created = service.create(request("Muster"), Language.GERMAN, "u1");

        assertThat(created.createdAt()).isNotNull();
        assertThat(created.version()).isZero();
    }

    @Test
    void updateReturnsTheIncrementedVersion() {
        HtmlLetterTemplateDto created = service.create(request("Muster"), Language.GERMAN, "u1");

        HtmlLetterTemplateDto updated = service.update(created.id(), request("Geändert"), Language.GERMAN, "u1");

        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    /**
     * Two tabs open on version 0: the first save wins and moves the row to version 1,
     * the second still sends 0 and must be refused rather than overwrite the first.
     */
    @Test
    void updateWithAStaleVersionIsRejected() {
        HtmlLetterTemplateDto created = service.create(request("Muster"), Language.GERMAN, "u1");
        service.update(created.id(), request("Tab B", created.version()), Language.GERMAN, "u1");

        assertThatThrownBy(() ->
                service.update(created.id(), request("Tab A", created.version()), Language.GERMAN, "u1"))
                .isInstanceOf(ApiException.Conflict.class);
        assertThat(service.find(created.id(), "u1").name()).isEqualTo("Tab B");
    }

    @Test
    void updateWithoutAVersionIsNotChecked() {
        HtmlLetterTemplateDto created = service.create(request("Muster"), Language.GERMAN, "u1");
        service.update(created.id(), request("Erste"), Language.GERMAN, "u1");

        HtmlLetterTemplateDto updated = service.update(created.id(), request("Zweite"), Language.GERMAN, "u1");

        assertThat(updated.name()).isEqualTo("Zweite");
    }
}
