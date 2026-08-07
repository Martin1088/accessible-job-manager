package de.samply.manager.jobimport.extractor;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tier 4: contact person from the HTML body.
 *
 * This is the only field that neither the API nor JSON-LD provide - it
 * lives exclusively in running text ("Your contact: Ms. Sodoli ..."). That
 * makes it structurally the weakest field, and it's deliberately ranked low:
 * the UI flags it as "please review".
 *
 * Gender:
 *   - a known salutation word (see jobimport/salutations.properties, e.g.
 *     "Herr"/"Frau", "Mr"/"Mrs"/"Ms") next to the name -> ADAPTER-safe
 *   - no salutation                                     -> null (never guessed)
 *
 * The salutation words, academic titles, and "contact section" anchor
 * phrases are all loaded from resources (jobimport/salutations.properties,
 * jobimport/academic-titles.txt, jobimport/contact-anchors.txt) rather than
 * hardcoded here, so new languages/phrasings can be added without a code
 * change.
 *
 * GDPR: the recruiter's name/email/phone is personal data (Art. 4). This
 * tier is the entry point for the PII-aware pre-processing step - right
 * here, not on the whole posting.
 */
@Component
@Order(4)
public class ContactBlockExtractor implements FieldExtractor {

    private static final List<String> CONTACT_ANCHORS = loadLines("jobimport/contact-anchors.txt");
    private static final List<String> ACADEMIC_TITLES = loadLines("jobimport/academic-titles.txt");

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final SalutationLookup salutations;
    private final Pattern salutationAndName;

    public ContactBlockExtractor(SalutationLookup salutations) {
        this.salutations = salutations;
        this.salutationAndName = Pattern.compile(
                "\\b(" + salutations.wordsPattern() + ")\\.?\\s+"
              + "(?:(?:" + titlesPattern() + ")\\.?\\s+)?"
              + "(?:\\p{Lu}[\\p{L}-]+\\s+)?"
              + "(\\p{Lu}[\\p{L}-]+)",
                Pattern.UNICODE_CHARACTER_CLASS);
    }

    @Override
    public ConfidenceTier tier() {
        return ConfidenceTier.HTML_REGEX;
    }

    @Override
    public ExtractionResult extract(ExtractionContext ctx) {
        String text = ctx.plainText();
        if (text == null || text.isBlank()) {
            return ExtractionResult.empty();
        }
        String scope = narrowToContactSection(text).orElse(text);

        var b = ExtractionResult.builder(ConfidenceTier.HTML_REGEX);

        Matcher sal = salutationAndName.matcher(scope);
        if (sal.find()) {
            String salutationWord = sal.group(1);
            String lastName = sal.group(2);
            b.contactLastName(lastName);
            salutations.gender(salutationWord)
                    .ifPresent(g -> b.contactGender(g, ConfidenceTier.ADAPTER));
        }
        Matcher mail = EMAIL.matcher(scope);
        if (mail.find()) {
            b.contactEmail(mail.group());
        }

        return b.build();
    }

    private Optional<String> narrowToContactSection(String text) {
        int best = -1;
        for (String anchor : CONTACT_ANCHORS) {
            int i = text.indexOf(anchor);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        if (best < 0) return Optional.empty();
        int end = Math.min(text.length(), best + 400);
        return Optional.of(text.substring(best, end));
    }

    private static String titlesPattern() {
        return ACADEMIC_TITLES.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElseThrow(() -> new IllegalStateException("jobimport/academic-titles.txt is empty"));
    }

    private static List<String> loadLines(String classpathResource) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpathResource).getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + classpathResource, e);
        }
        return lines;
    }
}
