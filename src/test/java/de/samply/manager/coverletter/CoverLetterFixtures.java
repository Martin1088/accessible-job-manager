package de.samply.manager.coverletter;

import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.types.Gender;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared fixtures for the cover letter tests. The message source deliberately reads
 * the real {@code messages*.properties} instead of a stub, so a missing or renamed
 * key fails a test here rather than at runtime.
 */
final class CoverLetterFixtures {

    private CoverLetterFixtures() {}

    static MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    static CoverLetterAssembler assembler() {
        MessageSource messageSource = messageSource();
        return new CoverLetterAssembler(
                new CoverLetterLabels(messageSource),
                new MarkupSanitizer(),
                new PlaceholderResolver(),
                new StyleSettingsValidator(messageSource));
    }

    static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    static CompanyPosition position() {
        CompanyLocation location = new CompanyLocation();
        location.setStreet("Hauptstraße 5");
        location.setPostcode("10115");
        location.setCity("Berlin");

        Company company = new Company();
        company.setName("Muster GmbH");
        company.setLocations(new ArrayList<>(List.of(location)));
        location.setCompany(company);

        CompanyPosition position = new CompanyPosition();
        position.setTitle("Java-Entwicklerin");
        position.setContactGender(Gender.FEMALE);
        position.setContactLastName("Meier");
        position.setCompany(company);
        return position;
    }

    static CoverLetterTemplate.Sender sender() {
        return new CoverLetterTemplate.Sender(
                "Jane Doe", "Musterweg 1", "54321", "Springfield", "jane@example.com", "+49 123 456");
    }

    static CoverLetterTemplate template(List<CoverLetterBlock> blocks) {
        return new CoverLetterTemplate(sender(), null, null, blocks, null, List.of(), null);
    }

    static CoverLetterBlock paragraph(String text) {
        return new CoverLetterBlock(BlockType.PARAGRAPH, text, List.of());
    }
}
