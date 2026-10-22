package de.ajm.manager.matching;

import de.ajm.manager.matching.CvMatchResult.MatchLevel;
import de.ajm.manager.matching.CvMatchResult.SkillAssessment;
import de.ajm.manager.matching.CvMatchResult.SkillCategory;
import de.ajm.manager.matching.CvMatchResult.SkillImportance;
import de.ajm.manager.matching.CvMatchResult.SkillStatus;
import de.ajm.manager.matching.CvMatchSpec.RawMatch;
import de.ajm.manager.matching.CvMatchSpec.RawSkill;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Turns the model's answer into a result the page can show without second-guessing it.
 *
 * <p>A pure function of the answer and the two texts it was given, so every rule here is
 * testable without a model. The rules, in the order they matter:
 * <ul>
 *   <li><b>Quotes are checked against their source.</b> A {@code postingQuote} that does
 *       not occur in the posting, or a {@code cvEvidence} that does not occur in the CV,
 *       becomes null. The page presents both as the texts' own words; a model that
 *       paraphrased or invented one would put words into the CV it does not contain,
 *       which is worse than showing no quote.</li>
 *   <li><b>A requirement with an unknown category, importance or status is dropped</b>,
 *       as is one without a name or a repeat of one already listed - one bad entry costs
 *       itself, not the answer.</li>
 *   <li><b>{@code level} outside its values is derived</b> from the requirements rather
 *       than failing the request; see {@link #levelOf}.</li>
 *   <li>Suggestions only accompany MISSING and PARTIAL, strings are capped, language codes
 *       are two-letter ISO 639-1 or null.</li>
 * </ul>
 */
final class CvMatchGuard {

    static final int MAX_SKILLS = 40;
    private static final int MAX_NAME = 120;
    private static final int MAX_QUOTE = 400;
    private static final int MAX_TEXT = 1_000;
    private static final int MAX_TITLE = 200;
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private CvMatchGuard() {}

    /** @return the cleaned result, or null when no usable requirement is left */
    static CvMatchResult clean(RawMatch raw, String postingText, String cvText,
                               UUID cvDocumentId, String language, LocalDateTime now) {
        if (raw == null || raw.skills() == null) {
            return null;
        }
        String posting = normalized(postingText);
        String cv = normalized(cvText);

        List<SkillAssessment> skills = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RawSkill rawSkill : raw.skills()) {
            SkillAssessment skill = skill(rawSkill, posting, cv);
            if (skill != null && seen.add(normalized(skill.skill())) && skills.size() < MAX_SKILLS) {
                skills.add(skill);
            }
        }
        if (skills.isEmpty()) {
            return null;
        }

        MatchLevel level = valueOf(MatchLevel.class, raw.level());
        return new CvMatchResult(
                level != null ? level : levelOf(skills),
                capped(raw.summary(), CvMatchSpec.MAX_FIELD_LENGTH, ""),
                cvDocumentId,
                capped(raw.postingTitle(), MAX_TITLE, null),
                capped(raw.company(), MAX_TITLE, null),
                language,
                languageCode(raw.postingLanguage()),
                languageCode(raw.cvLanguage()),
                now,
                List.copyOf(skills));
    }

    /**
     * The level when the model's own is unusable: STRONG with no required requirement
     * missing, PARTIAL with at most a third of them missing, WEAK beyond that. A posting
     * that marks nothing as required is judged on all of its requirements.
     */
    static MatchLevel levelOf(List<SkillAssessment> skills) {
        List<SkillAssessment> required = skills.stream()
                .filter(s -> s.importance() == SkillImportance.REQUIRED).toList();
        List<SkillAssessment> judged = required.isEmpty() ? skills : required;
        long missing = judged.stream().filter(s -> s.status() == SkillStatus.MISSING).count();
        if (missing == 0) return MatchLevel.STRONG;
        return missing * 3 <= judged.size() ? MatchLevel.PARTIAL : MatchLevel.WEAK;
    }

    private static SkillAssessment skill(RawSkill raw, String posting, String cv) {
        if (raw == null) return null;
        String name = capped(raw.skill(), MAX_NAME, null);
        SkillCategory category = valueOf(SkillCategory.class, raw.category());
        SkillImportance importance = valueOf(SkillImportance.class, raw.importance());
        SkillStatus status = valueOf(SkillStatus.class, raw.status());
        if (name == null || category == null || importance == null || status == null) {
            return null;
        }
        return new SkillAssessment(
                name, category, importance, status,
                verifiedQuote(raw.postingQuote(), posting),
                verifiedQuote(raw.cvEvidence(), cv),
                capped(raw.reasoning(), MAX_TEXT, ""),
                status == SkillStatus.MET ? null : capped(raw.suggestion(), MAX_TEXT, null));
    }

    /**
     * The quote when it occurs in the source, ignoring case, line breaks and surrounding
     * quotation marks - a PDF's text breaks lines where the page did, which a model
     * quoting the sentence does not reproduce.
     */
    private static String verifiedQuote(String quote, String normalizedSource) {
        String value = capped(quote, MAX_QUOTE, null);
        if (value == null) return null;
        String needle = normalized(value.replaceAll("^[\"'„“”«»]+|[\"'„“”«»]+$", ""));
        return !needle.isEmpty() && normalizedSource.contains(needle) ? value : null;
    }

    private static <E extends Enum<E>> E valueOf(Class<E> type, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String languageCode(String value) {
        if (value == null) return null;
        String code = value.trim().toLowerCase(Locale.ROOT);
        return LANGUAGE_CODE.matcher(code).matches() ? code : null;
    }

    private static String capped(String value, int max, String whenBlank) {
        if (value == null || value.isBlank()) return whenBlank;
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String normalized(String text) {
        return text == null ? "" : WHITESPACE.matcher(text).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
