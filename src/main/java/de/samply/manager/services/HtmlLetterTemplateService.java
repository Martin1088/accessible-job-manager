package de.samply.manager.services;

import de.samply.manager.coverletter.BlockType;
import de.samply.manager.coverletter.CoverLetterBlock;
import de.samply.manager.coverletter.CoverLetterHtmlService;
import de.samply.manager.coverletter.CoverLetterTemplate;
import de.samply.manager.coverletter.StyleSettingsValidator;
import de.samply.manager.dto.CoverLetterRenderRequest;
import de.samply.manager.dto.HtmlLetterTemplateRequest;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.HtmlLetterTemplate;
import de.samply.manager.repository.HtmlLetterTemplateRepository;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.types.Block;
import de.samply.manager.types.BlockKey;
import de.samply.manager.types.Language;
import de.samply.manager.types.LayoutLetterKey;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HtmlLetterTemplateService {

    private final HtmlLetterTemplateRepository repository;
    private final UserProfileRepository userProfileRepository;
    private final CoverLetterHtmlService coverLetterHtmlService;
    private final StyleSettingsValidator styleSettingsValidator;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public List<HtmlLetterTemplate> findAll(String userId) {
        return repository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public HtmlLetterTemplate find(UUID id, String userId) {
        return owned(id, userId);
    }

    @Transactional
    public HtmlLetterTemplate create(HtmlLetterTemplateRequest request, Language language, String userId) {
        return repository.save(HtmlLetterTemplate.builder()
                .userId(userId)
                .layoutLetter(layoutOf(request))
                .style(styleSettingsValidator.validated(request == null ? null : request.style()))
                .blocks(blocksOf(request, language))
                .build());
    }

    @Transactional
    public HtmlLetterTemplate update(UUID id, HtmlLetterTemplateRequest request, Language language, String userId) {
        HtmlLetterTemplate template = owned(id, userId);
        template.setLayoutLetter(layoutOf(request));
        template.setStyle(styleSettingsValidator.validated(request == null ? null : request.style()));
        template.setBlocks(blocksOf(request, language));
        return repository.save(template);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        repository.delete(owned(id, userId));
    }

    /**
     * Rebuilds the renderable letter from a stored template plus the parts that belong
     * to this one sending. Placeholders inside the blocks are left untouched - they are
     * resolved further down the pipeline, against the application being rendered for.
     */
    @Transactional(readOnly = true)
    public CoverLetterTemplate asCoverLetterTemplate(UUID id, CoverLetterRenderRequest request, String userId) {
        HtmlLetterTemplate stored = owned(id, userId);

        List<CoverLetterBlock> body = new ArrayList<>();
        String subject = null;
        String greeting = null;
        String closing = null;

        for (Block block : stored.getBlocks()) {
            switch (block.key()) {
                case SUBJECT -> subject = block.content();
                case SALUTATION -> greeting = block.content();
                case REGARDS -> closing = block.content();
                case PARAGRAPH -> body.add(new CoverLetterBlock(BlockType.PARAGRAPH, block.content(), block.items()));
                case HEADING -> body.add(new CoverLetterBlock(BlockType.HEADING, block.content(), block.items()));
                case BULLET_LIST -> body.add(new CoverLetterBlock(BlockType.BULLET_LIST, block.content(), block.items()));
            }
        }

        return new CoverLetterTemplate(
                sender(userId),
                subject,
                greeting,
                body,
                closing,
                request == null ? List.of() : request.attachments(),
                stored.getStyle()).normalized();
    }

    /** The sender block is the caller's profile; no letter form asks for it again. */
    private CoverLetterTemplate.Sender sender(String userId) {
        return userProfileRepository.findById(userId)
                .map(profile -> new CoverLetterTemplate.Sender(
                        profile.getName(), profile.getStreet(), profile.getPostalCode(),
                        profile.getCity(), profile.getEmail(), profile.getPhone()))
                .orElse(null);
    }

    private List<Block> blocksOf(HtmlLetterTemplateRequest request, Language language) {
        if (request != null && request.blocks() != null && !request.blocks().isEmpty()) {
            return request.blocks();
        }
        return skeleton(language);
    }

    private List<Block> skeleton(Language language) {
        CoverLetterTemplate defaults = coverLetterHtmlService.defaultTemplate(null, language);

        List<Block> blocks = new ArrayList<>();
        for (CoverLetterBlock block : defaults.blocks()) {
            blocks.add(new Block(UUID.randomUUID(), BlockKey.valueOf(block.type().name()), block.text(), block.items()));
        }
        blocks.add(new Block(UUID.randomUUID(), BlockKey.REGARDS, defaults.closing(), List.of()));
        return blocks;
    }

    private LayoutLetterKey layoutOf(HtmlLetterTemplateRequest request) {
        return request == null || request.layoutLetter() == null
                ? LayoutLetterKey.DIN5008_COVER_LETTER_B
                : request.layoutLetter();
    }

    private HtmlLetterTemplate owned(UUID id, String userId) {
        HtmlLetterTemplate template = repository.findById(id)
                .orElseThrow(() -> new ApiException.NotFound(
                        messageSource.getMessage("error.letterTemplate.notFound", null, Locale.ROOT)));
        if (!template.getUserId().equals(userId)) {
            throw new ApiException.Forbidden();
        }
        return template;
    }
}
