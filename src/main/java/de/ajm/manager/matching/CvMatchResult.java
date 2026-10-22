package de.ajm.manager.matching;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One CV held against one posting - the shape {@code AppClient/src/app/model/cv-match.ts}
 * reads. Every value has passed {@link CvMatchGuard}; nothing here is the model's raw word.
 *
 * @param language        the language the explanations were asked for, which the page
 *                        puts on the result's {@code lang} when it differs from its own
 * @param postingLanguage the language the posting quotes are in, ISO 639-1, or null
 * @param cvLanguage      the language the CV quotes are in, ISO 639-1, or null
 */
public record CvMatchResult(
        MatchLevel level,
        String summary,
        UUID cvDocumentId,
        String postingTitle,
        String company,
        String language,
        String postingLanguage,
        String cvLanguage,
        LocalDateTime generatedAt,
        List<SkillAssessment> skills) {

    public enum MatchLevel { STRONG, PARTIAL, WEAK }

    public enum SkillStatus { MET, PARTIAL, MISSING }

    public enum SkillImportance { REQUIRED, NICE_TO_HAVE }

    public enum SkillCategory { TECHNICAL, LANGUAGE, EXPERIENCE, EDUCATION, CERTIFICATE, SOFT_SKILL }

    /**
     * One requirement of the posting.
     *
     * @param postingQuote the requirement in the posting's own words - verified to occur there
     * @param cvEvidence   the CV passage that covers it - verified to occur there
     * @param suggestion   how to close the gap; only for MISSING and PARTIAL
     */
    public record SkillAssessment(
            String skill,
            SkillCategory category,
            SkillImportance importance,
            SkillStatus status,
            String postingQuote,
            String cvEvidence,
            String reasoning,
            String suggestion) {}
}
