package de.samply.manager.coverletter;

import de.samply.manager.model.Company;
import de.samply.manager.services.ApplicationService;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Entry point for the HTML cover letter provider: loads the application the letter
 * is written for, checks ownership, assembles the letter once and renders it into
 * whichever format was asked for.
 * <p>
 * The assembly step is shared by all three formats on purpose - PDF, HTML and the
 * linearized text preview are three renderings of one letter, not three pipelines.
 */
@Service
@RequiredArgsConstructor
public class CoverLetterHtmlService {

    private final ApplicationService applicationService;
    private final CoverLetterAssembler assembler;
    private final HtmlCoverLetterRenderer htmlRenderer;
    private final TextCoverLetterRenderer textRenderer;
    private final HtmlToPdfConverter pdfConverter;
    private final CoverLetterLabels labels;

    /** A rendered letter together with everything the controller needs to serve it. */
    public record RenderedLetter(RenderFormat format, byte[] content, String filename, Language language) {}

    /** A letter prepared for an email client: recipient, subject line and body text. */
    public record EmailDraft(String to, String subject, String body) {}

    @Transactional(readOnly = true)
    public RenderedLetter render(Long applicationId, CoverLetterTemplate template, RenderFormat format, String userId) {
        CompanyPosition position = ownedPosition(applicationId, userId);
        return renderFor(position, template, format, languageOf(position));
    }

    /**
     * The same letter handed to an email client instead of a printer: the linearized
     * text as the body, and the recipient taken from the position being applied for.
     * <p>
     * Subject and body come off the same assembled model the PDF is printed from, so a
     * template that writes its own subject line keeps it here rather than being given
     * a second one derived somewhere else.
     */
    @Transactional(readOnly = true)
    public EmailDraft renderAsEmail(Long applicationId, CoverLetterTemplate template, String userId) {
        CompanyPosition position = ownedPosition(applicationId, userId);
        CoverLetterModel letter = assembler.assemble(template, position, languageOf(position));
        return new EmailDraft(position.getEmail(), letter.subject(), textRenderer.render(letter));
    }

    /**
     * Renders a template against the sample recipient, so a letter can be proofread
     * before it is tied to an application. The sample position is built in memory and
     * never stored - this is the same pipeline as {@link #render}, only the recipient
     * differs, so what the preview shows is what a real application would print.
     */
    public RenderedLetter renderSample(CoverLetterTemplate template, RenderFormat format, Language language) {
        Language letterLanguage = language == null ? Language.GERMAN : language;
        return renderFor(samplePosition(letterLanguage), template, format, letterLanguage);
    }

    private RenderedLetter renderFor(CompanyPosition position, CoverLetterTemplate template,
                                     RenderFormat format, Language language) {
        CoverLetterModel letter = assembler.assemble(template, position, language);

        return switch (format) {
            case PDF -> new RenderedLetter(format, pdfConverter.toPdf(htmlRenderer.render(letter)),
                    fileBaseName(position, language) + ".pdf", language);
            case HTML -> new RenderedLetter(format, bytes(htmlRenderer.render(letter)),
                    fileBaseName(position, language) + ".html", language);
            case TEXT -> new RenderedLetter(format, bytes(textRenderer.render(letter)),
                    fileBaseName(position, language) + ".txt", language);
        };
    }

    /** A detached stand-in recipient; every value comes from the message bundle. */
    private CompanyPosition samplePosition(Language language) {
        Company company = new Company();
        company.setName(labels.label("coverLetter.sample.company", language));

        CompanyLocation location = new CompanyLocation();
        location.setStreet(labels.label("coverLetter.sample.street", language));
        location.setPostcode(labels.label("coverLetter.sample.postcode", language));
        location.setCity(labels.label("coverLetter.sample.city", language));
        location.setCountry(labels.label("coverLetter.sample.country", language));
        location.setCompany(company);
        company.getLocations().add(location);

        CompanyPosition position = new CompanyPosition();
        position.setTitle(labels.label("coverLetter.sample.position", language));
        position.setContactGender(Gender.FEMALE);
        position.setContactLastName(labels.label("coverLetter.sample.contactLastName", language));
        position.setApplyLanguage(language);
        position.setCompany(company);
        company.getPositions().add(position);

        return position;
    }

    /**
     * A starting template for the block editor: the user's sender block, the DIN 5008
     * defaults and a skeleton of paragraphs that already demonstrates the placeholder
     * syntax. The equivalent of the .docx path's "personalize" step, except the result
     * is editable data instead of a binary file.
     */
    public CoverLetterTemplate defaultTemplate(CoverLetterTemplate.Sender sender, Language language) {
        CoverLetterTemplate.Sender normalized =
                (sender == null ? new CoverLetterTemplate.Sender(null, null, null, null, null, null) : sender)
                        .normalized();

        List<CoverLetterBlock> blocks = List.of(
                paragraph(labels.label("coverLetter.skeleton.opening", language)),
                paragraph(labels.label("coverLetter.skeleton.motivation", language)),
                paragraph(labels.label("coverLetter.skeleton.outro", language)));

        return new CoverLetterTemplate(
                normalized,
                null,
                null,
                blocks,
                labels.label("coverLetter.closing", language),
                List.of(),
                StyleSettings.din5008FormB());
    }

    private CoverLetterBlock paragraph(String text) {
        return new CoverLetterBlock(BlockType.PARAGRAPH, text, List.of());
    }

    private CompanyPosition ownedPosition(Long applicationId, String userId) {
        return applicationService.findOwned(applicationId, userId).getCompanyPosition();
    }

    /** The letter follows the language the position is applied for, German by default. */
    private Language languageOf(CompanyPosition position) {
        return position.getApplyLanguage() != null ? position.getApplyLanguage() : Language.GERMAN;
    }

    private String fileBaseName(CompanyPosition position, Language language) {
        return labels.label("coverLetter.fileLabel", language)
                + "_" + position.getCompany().getName().replaceAll("\\s+", "_");
    }

    private byte[] bytes(String content) {
        return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
