package de.samply.manager.coverletter;

import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Turns the editable {@link CoverLetterTemplate} plus the application it is written
 * for into a fully resolved {@link CoverLetterModel}: recipient block from the
 * company, defaults for subject/greeting/closing from the message bundle, markup
 * sanitized and placeholders substituted.
 * <p>
 * This is the one place letter content is derived. Both output formats consume its
 * result, so a linearized preview can never describe a different letter than the PDF.
 */
@Component
@RequiredArgsConstructor
public class CoverLetterAssembler {

    private final CoverLetterLabels labels;
    private final MarkupSanitizer sanitizer;
    private final PlaceholderResolver placeholders;
    private final StyleSettingsValidator styleValidator;

    public CoverLetterModel assemble(CoverLetterTemplate rawTemplate, CompanyPosition position, Language language) {
        CoverLetterTemplate template = (rawTemplate == null
                ? new CoverLetterTemplate(null, null, null, null, null, null, null)
                : rawTemplate).normalized();

        Map<String, String> values = placeholderValues(template.sender(), position, language);

        return new CoverLetterModel(
                returnAddressLine(template.sender()),
                recipientLines(position.getCompany()),
                infoLine(template.sender(), values.get("date")),
                plain(template.subject(), values,
                        () -> labels.label("coverLetter.subjectPrefix", language) + " " + nullToEmpty(position.getTitle())),
                plain(template.greeting(), values, () -> labels.greeting(position, language)),
                template.blocks().stream().map(block -> assembleBlock(block, values)).toList(),
                plain(template.closing(), values, () -> labels.label("coverLetter.closing", language)),
                template.sender().name(),
                labels.label("coverLetter.attachmentsLabel", language),
                template.attachments().stream().map(a -> placeholders.resolvePlain(a, values)).toList(),
                styleValidator.validated(template.style()),
                language);
    }

    /**
     * Placeholders the editor may reference. Kept as one flat map so the same set is
     * available to blocks, subject, greeting and attachment lines alike.
     */
    public Map<String, String> placeholderValues(CoverLetterTemplate.Sender sender,
                                                 CompanyPosition position,
                                                 Language language) {
        Company company = position.getCompany();
        CompanyLocation location = company.getLocations().isEmpty() ? null : company.getLocations().get(0);

        Map<String, String> values = new LinkedHashMap<>();
        values.put("company", nullToEmpty(company.getName()));
        values.put("street", location == null ? "" : nullToEmpty(location.getStreet()));
        values.put("postalCode", location == null ? "" : nullToEmpty(location.getPostcode()));
        values.put("city", location == null ? "" : nullToEmpty(location.getCity()));
        values.put("country", location == null ? "" : nullToEmpty(location.getCountry()));
        values.put("position", nullToEmpty(position.getTitle()));
        values.put("contact", labels.salutation(position, language));
        values.put("contactLastName", nullToEmpty(position.getContactLastName()));
        values.put("date", formatDate(language));
        values.put("senderName", sender.name());
        values.put("senderStreet", sender.street());
        values.put("senderPostalCode", sender.postalCode());
        values.put("senderCity", sender.city());
        values.put("senderEmail", sender.email());
        values.put("senderPhone", sender.phone());
        return values;
    }

    private CoverLetterModel.Block assembleBlock(CoverLetterBlock block, Map<String, String> values) {
        return new CoverLetterModel.Block(
                block.type(),
                resolveMarkup(block.text(), values),
                block.items().stream().map(item -> resolveMarkup(item, values)).toList());
    }

    /** Sanitize first, then substitute HTML-escaped values - see {@link MarkupSanitizer}. */
    private String resolveMarkup(String raw, Map<String, String> values) {
        return placeholders.resolveEscaped(sanitizer.sanitize(raw), values);
    }

    private String plain(String override, Map<String, String> values, java.util.function.Supplier<String> fallback) {
        if (override == null || override.isBlank()) {
            return fallback.get();
        }
        return placeholders.resolvePlain(override, values);
    }

    /** DIN 5008 return address: one small line above the recipient, separated by middots. */
    private String returnAddressLine(CoverLetterTemplate.Sender sender) {
        return joinNonBlank(" · ", sender.name(), sender.street(),
                joinNonBlank(" ", sender.postalCode(), sender.city()));
    }

    private List<String> recipientLines(Company company) {
        CompanyLocation location = company.getLocations().isEmpty() ? null : company.getLocations().get(0);
        List<String> lines = new ArrayList<>();
        lines.add(nullToEmpty(company.getName()));
        if (location != null) {
            lines.add(nullToEmpty(location.getStreet()));
            lines.add(joinNonBlank(" ", location.getPostcode(), location.getCity()));
        }
        return lines.stream().filter(line -> !line.isBlank()).toList();
    }

    /** The information block: place and date, e.g. {@code Springfield, 11.08.2026}. */
    private String infoLine(CoverLetterTemplate.Sender sender, String date) {
        return joinNonBlank(", ", sender.city(), date);
    }

    private String formatDate(Language language) {
        return LocalDate.now().format(DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(language != null ? language.locale() : Language.GERMAN.locale()));
    }

    private String joinNonBlank(String separator, String... parts) {
        return Stream.of(parts)
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + separator + b)
                .orElse("");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
