package de.samply.manager.coverletter;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Application;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

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

    private final ApplicationRepository applicationRepository;
    private final CoverLetterAssembler assembler;
    private final HtmlCoverLetterRenderer htmlRenderer;
    private final TextCoverLetterRenderer textRenderer;
    private final HtmlToPdfConverter pdfConverter;
    private final CoverLetterLabels labels;
    private final MessageSource messageSource;

    /** A rendered letter together with everything the controller needs to serve it. */
    public record RenderedLetter(RenderFormat format, byte[] content, String filename, Language language) {}

    @Transactional(readOnly = true)
    public RenderedLetter render(Long applicationId, CoverLetterTemplate template, RenderFormat format, String userId) {
        CompanyPosition position = ownedPosition(applicationId, userId);
        Language language = languageOf(position);

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
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException.NotFound(
                        messageSource.getMessage("error.coverLetter.applicationNotFound", null, Locale.ROOT)));
        if (!application.getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return application.getCompanyPosition();
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
